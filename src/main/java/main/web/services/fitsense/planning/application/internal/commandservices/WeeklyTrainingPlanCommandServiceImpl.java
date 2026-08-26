package main.web.services.fitsense.planning.application.internal.commandservices;

import main.web.services.fitsense.planning.application.internal.outboundservices.acl.*;
import main.web.services.fitsense.planning.domain.exceptions.*;
import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.commands.*;
import main.web.services.fitsense.planning.domain.model.valueobjects.*;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanCommandService;
import main.web.services.fitsense.planning.infrastructure.generation.ai.PlanInputSnapshot;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.PlannedWorkoutRepository;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.WeeklyTrainingPlanRepository;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import main.web.services.fitsense.shared.infrastructure.json.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class WeeklyTrainingPlanCommandServiceImpl implements WeeklyTrainingPlanCommandService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyTrainingPlanCommandServiceImpl.class);

    private final WeeklyTrainingPlanRepository planRepository;
    private final PlannedWorkoutRepository plannedWorkoutRepository;
    private final PlanGenerationPipeline pipeline;
    private final PreviousWeekAssembler previousWeekAssembler;
    private final ExternalIamService externalIamService;
    private final ExternalProfilingService externalProfilingService;
    private final ExternalCatalogService externalCatalogService;
    private final ExternalConfigurationService externalConfigurationService;
    private final JsonSupport jsonSupport;

    public WeeklyTrainingPlanCommandServiceImpl(WeeklyTrainingPlanRepository planRepository,
                                                PlannedWorkoutRepository plannedWorkoutRepository,
                                                PlanGenerationPipeline pipeline,
                                                PreviousWeekAssembler previousWeekAssembler,
                                                ExternalIamService externalIamService,
                                                ExternalProfilingService externalProfilingService,
                                                ExternalCatalogService externalCatalogService,
                                                ExternalConfigurationService externalConfigurationService,
                                                JsonSupport jsonSupport) {
        this.planRepository = planRepository;
        this.plannedWorkoutRepository = plannedWorkoutRepository;
        this.pipeline = pipeline;
        this.previousWeekAssembler = previousWeekAssembler;
        this.externalIamService = externalIamService;
        this.externalProfilingService = externalProfilingService;
        this.externalCatalogService = externalCatalogService;
        this.externalConfigurationService = externalConfigurationService;
        this.jsonSupport = jsonSupport;
    }

    @Override
    @Transactional
    public Optional<WeeklyTrainingPlan> handle(GenerateWeeklyPlanCommand command) {
        if (!externalIamService.isEligibleForPlanGeneration(command.userId()))
            throw new DomainRuleViolationException(
                    "El usuario %d no esta activo en el estudio: no se le generan planes."
                            .formatted(command.userId()));

        var profile = externalProfilingService.fetchProfile(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil del usuario", command.userId()));

        var week = TrainingWeek.containing(command.weekStartDate());
        var previousActive = planRepository.findByUserIdAndWeekStartDateAndStatus(
                command.userId(), week.startDate(), PlanStatus.ACTIVE);

        if (previousActive.isPresent() && !command.replaceExisting())
            throw new PlanAlreadyExistsException(command.userId(), week.startDate());

        int divisor = externalConfigurationService.durationToRepsDivisor();
        var adjustment = command.adjustment() == null ? PlanAdjustment.none() : command.adjustment();

        // LOWER_DIFFICULTY baja el filtro un nivel antes de pedir el conjunto
        // elegible: es un cambio de QUE ejercicios existen, no de como se
        // prescriben, asi que debe aplicarse en la consulta al catalogo.
        int maxDifficulty = adjustment.maxDifficultyLevel() != null
                ? Math.max(1, adjustment.maxDifficultyLevel())
                : profile.maxDifficultyLevel();

        var eligible = externalCatalogService.fetchEligibleFor(profile, maxDifficulty);
        if (eligible.isEmpty())
            throw new main.web.services.fitsense.catalog.domain.exceptions.EmptyEligibleSetException(
                    command.userId());

        short weekNumber = resolveWeekNumber(command.userId(), previousActive.orElse(null));
        var previousWeek = previousWeekAssembler.assemble(
                command.userId(), week.previous().startDate(), divisor);

        var context = new PlanGenerationContext(command.userId(), weekNumber,
                week.startDate(), week.endDate(), profile, adjustment, previousWeek, eligible);

        var result = pipeline.run(context, divisor);
        var draft = result.draft();

        var snapshotJson = jsonSupport.write(PlanInputSnapshot.of(context));
        var zone = externalIamService.timezoneOf(command.userId());

        var plan = previousActive
                .map(previous -> WeeklyTrainingPlan.nextVersionOf(previous, draft.source(),
                        draft.modelName(), snapshotJson, result.attempts(),
                        adjustment.types().get(0).name(), draft.rationale()))
                .orElseGet(() -> WeeklyTrainingPlan.firstVersion(command.userId(), weekNumber,
                        week.startDate(), week.endDate(), draft.source(), draft.modelName(),
                        snapshotJson, result.attempts()));

        materialize(plan, draft, zone);
        plan.attachOutputSnapshot(jsonSupport.write(draft));

        // Reemplazar y vaciar el contexto ANTES de guardar el nuevo:
        // ux_plan_active solo admite un ACTIVE por usuario y semana.
        previousActive.ifPresent(previous -> {
            previous.markReplaced();
            planRepository.saveAndFlush(previous);
        });

        var saved = planRepository.save(plan);
        log.info("Plan {} generado para el usuario {} por {} en {} intento(s)",
                saved.getId(), command.userId(), draft.source(), result.attempts());
        return Optional.of(saved);
    }

    @Override
    @Transactional
    public void handle(StartPlannedWorkoutCommand command) {
        var workout = requireWorkout(command.plannedWorkoutId());
        workout.markInProgress();
        plannedWorkoutRepository.save(workout);
    }

    @Override
    @Transactional
    public void handle(RecordWorkoutOutcomeCommand command) {
        var workout = requireWorkout(command.plannedWorkoutId());
        workout.markOutcome(command.outcome(), command.skipReason());
        plannedWorkoutRepository.save(workout);
    }

    @Override
    @Transactional
    public void handle(SkipPlannedWorkoutCommand command) {
        var workout = requireWorkout(command.plannedWorkoutId());

        if (!workout.getPlan().getUserId().equals(command.userId()))
            throw new PlannedWorkoutNotFoundException(command.plannedWorkoutId());
        if (command.reason() == null)
            throw new DomainRuleViolationException(
                    "Indica por que no vas a hacer este entrenamiento: sin la causa el sistema no puede ajustar.");

        workout.markSkipped(command.reason());
        plannedWorkoutRepository.save(workout);
    }

    @Override
    @Transactional
    public void handle(ClosePlanWeekCommand command) {
        planRepository.findByUserIdAndWeekStartDateAndStatus(
                        command.userId(), command.weekStartDate(), PlanStatus.ACTIVE)
                .ifPresent(plan -> {
                    plan.markCompleted();
                    planRepository.save(plan);
                });
    }

    /**
     * Tarea diaria de 00:30. El skip_reason queda NULL a proposito: se le
     * pregunta al usuario despues. Rellenarlo con OTHER seria inventar un dato
     * que luego alimenta el modificador de 18.3.
     */
    @Override
    @Transactional
    public int handle(ExpireOverdueWorkoutsCommand command) {
        var overdue = plannedWorkoutRepository.findOverdue(command.now());
        overdue.forEach(workout -> workout.markOutcome(WorkoutStatus.SKIPPED, null));
        plannedWorkoutRepository.saveAll(overdue);
        if (!overdue.isEmpty())
            log.info("Marcados {} entrenamientos vencidos como SKIPPED", overdue.size());
        return overdue.size();
    }

    // ------------------------------------------------------------------ helpers

    private main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout requireWorkout(
            Long plannedWorkoutId) {
        return plannedWorkoutRepository.findById(plannedWorkoutId)
                .orElseThrow(() -> new PlannedWorkoutNotFoundException(plannedWorkoutId));
    }

    /**
     * week_number es la semana del participante en el estudio, no del calendario:
     * empieza en 1 con su primer plan. Un reemplazo conserva el numero, porque
     * sigue siendo la misma semana.
     */
    private short resolveWeekNumber(Long userId, WeeklyTrainingPlan previousActive) {
        if (previousActive != null) return previousActive.getWeekNumber();
        return planRepository.findFirstByUserIdOrderByWeekNumberDescPlanVersionDesc(userId)
                .map(plan -> (short) (plan.getWeekNumber() + 1))
                .orElse((short) 1);
    }

    private void materialize(WeeklyTrainingPlan plan, PlanDraft draft, ZoneId zone) {
        for (var draftWorkout : draft.workouts()) {
            // expires_at es 23:59 hora LOCAL del usuario: es lo que define que
            // entrenamientos siguen siendo exigibles.
            var expiresAt = draftWorkout.scheduledDate()
                    .atTime(LocalTime.of(23, 59))
                    .atZone(zone)
                    .toOffsetDateTime();

            var workout = plan.addWorkout(draftWorkout.scheduledDate(), draftWorkout.focus(),
                    draftWorkout.name(), draftWorkout.expectedDurationMinutes(), expiresAt);

            for (var draftExercise : draftWorkout.exercises()) {
                workout.addExercise(draftExercise.exerciseId(), draftExercise.prescriptionType(),
                        draftExercise.plannedSets(), draftExercise.plannedReps(),
                        draftExercise.plannedDurationSeconds(), draftExercise.targetLoadKg(),
                        draftExercise.restSeconds(), draftExercise.notes());
            }
        }
    }
}
