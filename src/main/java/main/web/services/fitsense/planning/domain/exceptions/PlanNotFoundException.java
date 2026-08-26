package main.web.services.fitsense.planning.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

public class PlanNotFoundException extends ResourceNotFoundException {

    public PlanNotFoundException(Long planId) {
        super("Plan", planId);
    }

    private PlanNotFoundException(String message) {
        super(message);
    }

    public static PlanNotFoundException active(Long userId) {
        return new PlanNotFoundException(
                "El usuario %d no tiene un plan activo para esta semana.".formatted(userId));
    }
}
