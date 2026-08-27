package main.web.services.fitsense.execution.application.internal.commandservices;

import main.web.services.fitsense.execution.application.internal.outboundservices.acl.ExternalConfigurationService;
import main.web.services.fitsense.execution.application.internal.outboundservices.acl.ExternalPlanningService;
import main.web.services.fitsense.execution.domain.exceptions.SessionAlreadyInProgressException;
import main.web.services.fitsense.execution.domain.exceptions.SessionNotFoundException;
import main.web.services.fitsense.execution.domain.exceptions.WorkoutNotAvailableException;
import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.commands.*;
import main.web.services.fitsense.execution.domain.model.valueobjects.CompletionThresholds;
import main.web.services.fitsense.execution.domain.model.valueobjects.ExerciseTarget;
import main.web.services.fitsense.execution.domain.model.valueobjects.SessionStatus;
import main.web.services.fitsense.execution.domain.services.WorkoutSessionCommandService;
import main.web.services.fitsense.execution.infrastructure.persistence.jpa.repositories.WorkoutSessionRepository;
import main.web.services.fitsense.planning.interfaces.acl.PlannedWorkoutView;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WorkoutSessionCommandServiceImpl implements WorkoutSessionCommandService {

    private final WorkoutSessionRepository sessionRepository;
    private final ExternalPlanningService externalPlanningService;
    private final ExternalConfigurationService externalConfigurationService;

    public WorkoutSessionCommandServiceImpl(WorkoutSessionRepository sessionRepository,
                                            ExternalPlanningService externalPlanningService,
                                            ExternalConfigurationService externalConfigurationService) {
        this.sessionRepository = sessionRepository;
        this.externalPlanningService = externalPlanningService;
        this.externalConfigurationService = externalConfigurationService;
    }

    @Override
    @Transactional
    public Optional<WorkoutSession> handle(StartWorkoutSessionCommand command) {
        var workout = requireOwnedWorkout(command.userId(), command.plannedWorkoutId());

        if (!workout.isOpen())
            throw WorkoutNotAvailableException.closed(command.plannedWorkoutId(), workout.status());
        if (workout.isExpired(OffsetDateTime.now()))
            throw WorkoutNotAvailableException.expired(command.plannedWorkoutId());

        sessionRepository.findByUserIdAndStatus(command.userId(), SessionStatus.IN_PROGRESS)
                .ifPresent(open -> {
                    throw new SessionAlreadyInProgressException(command.userId());
                });

        short attempt = (short) (sessionRepository.findLastAttemptNumber(command.plannedWorkoutId()) + 1);
        var session = WorkoutSession.start(command.userId(), command.plannedWorkoutId(),
                workout.planId(), attempt);

        var saved = sessionRepository.save(session);
        externalPlanningService.markInProgress(command.plannedWorkoutId());
        return Optional.of(saved);
    }

    @Override
    @Transactional
    public Optional<WorkoutSession> handle(RecordSessionExerciseCommand command) {
        var session = requireOwnedSession(command.userId(), command.sessionId());
        var workout = requireOwnedWorkout(command.userId(), session.getPlannedWorkoutId());

        var target = findTarget(workout, command.plannedExerciseId());
        session.recordExercise(target, command.actualSets(), command.actualRepsTotal(),
                command.actualDurationSeconds(), command.actualLoadKg(), command.skipReason(),
                externalConfigurationService.thresholds());

        return Optional.of(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public Optional<WorkoutSession> handle(FinishWorkoutSessionCommand command) {
        var session = requireOwnedSession(command.userId(), command.sessionId());
        var workout = requireOwnedWorkout(command.userId(), session.getPlannedWorkoutId());

        var thresholds = externalConfigurationService.thresholds();
        session.finish(command.sessionRpe(), command.satisfaction(), command.activeMinutes(),
                workout.exercises().size(), thresholds);

        return Optional.of(closeAndPropagate(session, thresholds));
    }

    @Override
    @Transactional
    public Optional<WorkoutSession> handle(AbandonWorkoutSessionCommand command) {
        var session = requireOwnedSession(command.userId(), command.sessionId());
        session.abandon();

        var saved = sessionRepository.save(session);
        // Abandonar no cierra el entrenamiento: sigue exigible hasta que venza,
        // porque el usuario todavia puede volver a intentarlo hoy.
        return Optional.of(saved);
    }

    @Override
    @Transactional
    public Optional<WorkoutSession> handle(ReportWorkoutCommand command) {
        var workout = requireOwnedWorkout(command.userId(), command.plannedWorkoutId());

        if (command.exercises() == null || command.exercises().isEmpty())
            throw new DomainRuleViolationException(
                    "Indica que ejercicios hiciste: un reporte vacio no es un entrenamiento.");

        short attempt = (short) (sessionRepository.findLastAttemptNumber(command.plannedWorkoutId()) + 1);
        var session = WorkoutSession.reported(command.userId(), command.plannedWorkoutId(),
                workout.planId(), attempt, command.performedAt());

        var thresholds = externalConfigurationService.thresholds();
        for (var reported : command.exercises()) {
            var target = findTarget(workout, reported.plannedExerciseId());
            session.recordExercise(target, reported.actualSets(), reported.actualRepsTotal(),
                    reported.actualDurationSeconds(), reported.actualLoadKg(),
                    reported.skipReason(), thresholds);
        }

        session.finish(command.sessionRpe(), command.satisfaction(), command.activeMinutes(),
                workout.exercises().size(), thresholds);

        return Optional.of(closeAndPropagate(session, thresholds));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Regla del intento valido: solo el ultimo intento finalizado cuenta.
     * <p>
     * El orden importa. ux_session_counted es un indice unico parcial sobre
     * planned_workout_id: hay que desmarcar los intentos anteriores y vaciar el
     * contexto de persistencia ANTES de guardar el nuevo, o la base rechaza el
     * insert por duplicado.
     */
    private WorkoutSession closeAndPropagate(WorkoutSession session, CompletionThresholds thresholds) {
        List<WorkoutSession> previouslyCounted =
                sessionRepository.findByPlannedWorkoutIdAndCountsTowardAdherenceTrue(
                        session.getPlannedWorkoutId());

        for (var previous : previouslyCounted) {
            if (!previous.getId().equals(session.getId())) {
                previous.supersede();
                sessionRepository.saveAndFlush(previous);
            }
        }

        var saved = sessionRepository.saveAndFlush(session);

        // El desenlace del entrenamiento sale del nivel de 17.2, no del estado
        // persistido: una sesion por debajo del 30 % se guarda como PARTIAL pero
        // el entrenamiento del plan se cierra como SKIPPED.
        var outcome = saved.completionLevel(thresholds).workoutOutcome();
        externalPlanningService.recordOutcome(saved.getPlannedWorkoutId(), outcome,
                saved.dominantSkipReason().orElse(null));

        return saved;
    }

    private PlannedWorkoutView requireOwnedWorkout(Long userId, Long plannedWorkoutId) {
        var workout = externalPlanningService.fetchPlannedWorkout(plannedWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Entrenamiento planificado", plannedWorkoutId));

        // Mismo 404 que si no existiera: no se confirma la existencia de recursos ajenos.
        if (!workout.userId().equals(userId))
            throw new ResourceNotFoundException("Entrenamiento planificado", plannedWorkoutId);

        return workout;
    }

    private WorkoutSession requireOwnedSession(Long userId, Long sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(session -> session.getUserId().equals(userId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    private ExerciseTarget findTarget(PlannedWorkoutView workout, Long plannedExerciseId) {
        return externalPlanningService.targetsOf(workout).stream()
                .filter(target -> target.plannedExerciseId().equals(plannedExerciseId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolationException(
                        "El ejercicio %d no pertenece a este entrenamiento."
                                .formatted(plannedExerciseId)));
    }
}
