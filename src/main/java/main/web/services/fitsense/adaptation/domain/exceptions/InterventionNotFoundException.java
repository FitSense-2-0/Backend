package main.web.services.fitsense.adaptation.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

public class InterventionNotFoundException extends ResourceNotFoundException {
    public InterventionNotFoundException(Long interventionId) {
        super("Intervencion", interventionId);
    }
}
