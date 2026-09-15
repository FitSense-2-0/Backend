package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Resultado de la generacion: el borrador y cuantos intentos costo.
 * <p>
 * attempts es short porque asi lo esperan nextVersionOf y firstVersion, y asi
 * lo devuelve el pipeline.
 */
public record GeneratedPlan(PlanDraft draft, short attempts) {}