package main.web.services.fitsense.execution.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

public class WorkoutNotAvailableException extends DomainRuleViolationException {

    public static WorkoutNotAvailableException expired(Long plannedWorkoutId) {
        return new WorkoutNotAvailableException(
                ("El entrenamiento %d ya vencio. Si lo hiciste, registralo con el reporte "
                        + "retroactivo en vez de abrir una sesion nueva.").formatted(plannedWorkoutId));
    }

    public static WorkoutNotAvailableException closed(Long plannedWorkoutId, String status) {
        return new WorkoutNotAvailableException(
                "El entrenamiento %d ya se cerro como %s.".formatted(plannedWorkoutId, status));
    }

    private WorkoutNotAvailableException(String message) {
        super(message);
    }
}
