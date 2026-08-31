package main.web.services.fitsense.analytics.application.internal.outboundservices.acl;

import main.web.services.fitsense.analytics.domain.model.valueobjects.WeekSessionInput;
import main.web.services.fitsense.execution.interfaces.acl.ExecutionContextFacade;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Capa anticorrupcion hacia execution: el numerador de la semana. */
@Service("analyticsExternalPlanningService")
public class ExternalExecutionService {

    private final ExecutionContextFacade executionContextFacade;

    public ExternalExecutionService(ExecutionContextFacade executionContextFacade) {
        this.executionContextFacade = executionContextFacade;
    }

    public List<WeekSessionInput> fetchWeekSessions(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        return executionContextFacade.fetchCountedSessions(userId, weekStart, weekEnd).stream()
                .map(session -> new WeekSessionInput(
                        session.plannedWorkoutId(),
                        session.completionPercentage(),
                        session.activeMinutes(),
                        session.sessionRpe(),
                        session.satisfaction(),
                        session.completedExercises(),
                        session.dominantSkipReason()))
                .toList();
    }

    /**
     * Dias sin ninguna sesion valida al cierre (17.5). Null si nunca entreno: no
     * es cero, y tampoco un numero grande arbitrario que marcaria como abandono
     * a alguien que acaba de inscribirse.
     */
    public Short daysSinceLastWorkout(Long userId, LocalDate weekEnd) {
        return executionContextFacade.fetchLastCountedSessionAt(userId)
                .map(at -> {
                    long days = ChronoUnit.DAYS.between(at.toLocalDate(), weekEnd);
                    return (short) Math.max(0, Math.min(days, Short.MAX_VALUE));
                })
                .orElse(null);
    }
}
