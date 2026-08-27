package main.web.services.fitsense.execution.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

/**
 * ux_session_active impide dos sesiones simultaneas del mismo usuario. Se
 * comprueba tambien aqui para devolver 422 con un mensaje util en vez de un
 * error de restriccion de la base.
 */
public class SessionAlreadyInProgressException extends DomainRuleViolationException {
    public SessionAlreadyInProgressException(Long userId) {
        super("Ya tienes una sesion en curso. Terminala o abandonala antes de empezar otra.");
    }
}
