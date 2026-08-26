package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Prescripcion efectiva dentro del plan. Enum propio y no el del catalogo: el
 * catalogo declara un valor por defecto, la planificacion decide el que
 * realmente se indico. Que hoy coincidan no los hace el mismo concepto.
 */
public enum PrescriptionType {
    SETS_REPS,
    DURATION
}
