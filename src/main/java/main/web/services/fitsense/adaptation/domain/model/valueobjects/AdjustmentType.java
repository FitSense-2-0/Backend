package main.web.services.fitsense.adaptation.domain.model.valueobjects;

/**
 * Debe coincidir con ck_ui_types_domain. Solo hay tipos de reduccion: la
 * progresion se expresa como NONE con un target_volume_change_pct positivo,
 * porque subir el volumen no es una intervencion sobre el participante sino la
 * evolucion normal del plan.
 */
public enum AdjustmentType {
    NONE,
    REDUCE_VOLUME,
    REDUCE_DURATION,
    REDUCE_DAYS,
    LOWER_DIFFICULTY,
    LOWER_LOAD
}
