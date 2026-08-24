package main.web.services.fitsense.profiling.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

public class UserProfileNotFoundException extends ResourceNotFoundException {
    public UserProfileNotFoundException(Long userId) {
        super("Todavia no has completado tu perfil (usuario " + userId + ").");
    }
}
