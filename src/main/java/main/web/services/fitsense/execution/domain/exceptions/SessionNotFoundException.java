package main.web.services.fitsense.execution.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

public class SessionNotFoundException extends ResourceNotFoundException {
    public SessionNotFoundException(Long sessionId) {
        super("Sesion", sessionId);
    }
}
