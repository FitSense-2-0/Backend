package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Debe coincidir con ck_plan_source. La proporcion de semanas RULE_ENGINE
 * frente a AI es un dato reportable de la tesis (20.6): si es alta, indica un
 * problema con el proveedor o con el prompt.
 */
public enum GenerationSource {
    AI,
    RULE_ENGINE
}
