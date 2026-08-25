package main.web.services.fitsense.catalog.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

public class ExerciseNotFoundException extends ResourceNotFoundException {
    public ExerciseNotFoundException(Long exerciseId) {
        super("Ejercicio", exerciseId);
    }
}
