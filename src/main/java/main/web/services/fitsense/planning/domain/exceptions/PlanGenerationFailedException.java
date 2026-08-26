package main.web.services.fitsense.planning.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

/**
 * Fallaron los dos intentos de IA y tambien el motor de reglas (19.4).
 * <p>
 * La semana queda sin plan activo: has_active_plan = false y la adherencia de
 * esa semana sera NULL, no 0. Es un dato del estudio, no un error que ocultar.
 */
public class PlanGenerationFailedException extends DomainRuleViolationException {
    public PlanGenerationFailedException(Long userId, String detail) {
        super("No se pudo generar un plan valido para el usuario %d. %s".formatted(userId, detail));
    }
}
