package main.web.services.fitsense.planning.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

public class PlannedWorkoutNotFoundException extends ResourceNotFoundException {
    public PlannedWorkoutNotFoundException(Long plannedWorkoutId) {
        super("Entrenamiento planificado", plannedWorkoutId);
    }
}
