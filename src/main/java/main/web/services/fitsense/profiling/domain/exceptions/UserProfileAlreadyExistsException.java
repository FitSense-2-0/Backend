package main.web.services.fitsense.profiling.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

public class UserProfileAlreadyExistsException extends DomainRuleViolationException {
    public UserProfileAlreadyExistsException(Long userId) {
        super("El usuario " + userId + " ya tiene un perfil. Usa PUT para actualizarlo.");
    }
}
