package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Los tipos de ajuste que la adaptacion puede ordenar (18.2 y 18.3). Vive en
 * planning porque es el generador quien debe entenderlos; adaptation los emite
 * como texto a traves de su facade.
 */
public enum AdjustmentType {
    NONE,
    REDUCE_VOLUME,
    REDUCE_DURATION,
    REDUCE_DAYS,
    LOWER_DIFFICULTY,
    LOWER_LOAD
}
