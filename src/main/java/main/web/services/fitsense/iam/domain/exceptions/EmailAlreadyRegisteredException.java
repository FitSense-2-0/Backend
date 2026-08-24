package main.web.services.fitsense.iam.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

public class EmailAlreadyRegisteredException extends DomainRuleViolationException {
    public EmailAlreadyRegisteredException(String email) {
        super("Ya existe una cuenta registrada con el correo " + email + ".");
    }
}
