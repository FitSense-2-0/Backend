package main.web.services.fitsense.execution.interfaces.acl;

import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.queries.GetSessionsByWeekQuery;
import main.web.services.fitsense.execution.domain.services.WorkoutSessionQueryService;
import main.web.services.fitsense.execution.infrastructure.persistence.jpa.repositories.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Unico punto de entrada a execution desde otros contextos. Lo consume analytics
 * para armar el numerador de la adherencia y para saber cuantos dias lleva el
 * participante sin entrenar.
 */
@Service
public class ExecutionContextFacade {

    private final WorkoutSessionQueryService queryService;
    private final WorkoutSessionRepository sessionRepository;

    public ExecutionContextFacade(WorkoutSessionQueryService queryService,
                                  WorkoutSessionRepository sessionRepository) {
        this.queryService = queryService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Solo las sesiones que cuentan: el ultimo intento finalizado de cada
     * entrenamiento.
     * <p>
     * El divisor viaja como parametro y no se lee aqui dentro por la misma razon
     * que los umbrales de 17.2: el dominio no consulta configuracion, y asi la
     * semana queda medida con la version vigente en ese momento.
     */
    @Transactional(readOnly = true)
    public List<SessionSummaryView> fetchCountedSessions(Long userId, LocalDate weekStart,
                                                         LocalDate weekEnd,
                                                         int durationToRepsDivisor) {
        return queryService.handle(new GetSessionsByWeekQuery(userId, weekStart, weekEnd)).stream()
                .map(session -> toView(session, durationToRepsDivisor))
                .toList();
    }

    /** Ultima sesion que conto, en cualquier semana. Base de days_since_last_workout. */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> fetchLastCountedSessionAt(Long userId) {
        return sessionRepository.findLastCountedSessionDate(userId);
    }

    private static SessionSummaryView toView(WorkoutSession session, int durationToRepsDivisor) {
        return new SessionSummaryView(
                session.getId(),
                session.getPlannedWorkoutId(),
                session.getPlanId(),
                session.getStartedAt(),
                session.getCompletionPercentage(),
                session.getStatus().name(),
                session.getActiveMinutes(),
                session.getSessionRpe(),
                session.getSatisfaction(),
                session.completedExerciseCount(),
                session.executedEquivalentVolume(durationToRepsDivisor),
                session.dominantSkipReason().map(Enum::name).orElse(null));
    }
}