package main.web.services.fitsense.planning.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

import java.time.LocalDate;

/**
 * Ya hay un plan activo para esa semana. Generar otro sin reemplazar el anterior
 * violaria ux_plan_active y, sobre todo, dejaria la semana con dos denominadores
 * de adherencia.
 */
public class PlanAlreadyExistsException extends DomainRuleViolationException {
    public PlanAlreadyExistsException(Long userId, LocalDate weekStartDate) {
        super("El usuario %d ya tiene un plan activo para la semana del %s."
                .formatted(userId, weekStartDate));
    }
}
