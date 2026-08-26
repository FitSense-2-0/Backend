package main.web.services.fitsense.planning.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;

import java.util.List;

/**
 * El borrador no paso las validaciones de 19.3. Lleva la lista COMPLETA de
 * problemas, no el primero: en el segundo intento esa lista se reenvia a la IA
 * para que corrija, tal como manda la politica de fallo de 19.4.
 */
public class InvalidPlanDraftException extends DomainRuleViolationException {

    private final transient List<String> problems;

    public InvalidPlanDraftException(List<String> problems) {
        super("El plan propuesto no cumple las restricciones: " + String.join(" | ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
