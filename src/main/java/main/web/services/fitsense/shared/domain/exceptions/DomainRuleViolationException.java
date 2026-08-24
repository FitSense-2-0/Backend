package main.web.services.fitsense.shared.domain.exceptions;

/** Se violo una invariante del dominio. Se traduce a HTTP 422. */
public class DomainRuleViolationException extends RuntimeException {
    public DomainRuleViolationException(String message) {
        super(message);
    }
}
