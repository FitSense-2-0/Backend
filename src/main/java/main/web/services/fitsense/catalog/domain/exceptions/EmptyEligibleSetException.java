package main.web.services.fitsense.catalog.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

/**
 * Perfil demasiado restrictivo: no queda ningun ejercicio que proponer.
 * Es un fallo de datos, no del generador, y hay que verlo explicito antes de
 * pedirle un plan a la IA.
 */
public class EmptyEligibleSetException extends DomainRuleViolationException {
    public EmptyEligibleSetException(Long userId) {
        super(("No hay ejercicios elegibles para el usuario %d. Revisa el equipamiento declarado, "
                + "el nivel de condicion fisica y los ejercicios bloqueados.").formatted(userId));
    }
}
