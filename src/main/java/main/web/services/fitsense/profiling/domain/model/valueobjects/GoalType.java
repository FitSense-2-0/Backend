package main.web.services.fitsense.profiling.domain.model.valueobjects;

/**
 * Cuatro categorias. Las metas de carrera quedan fuera del MVP porque el
 * catalogo no tiene ejercicios de carrera continua ni intervalos (seccion 2.6).
 */
public enum GoalType {
    LOSE_WEIGHT,
    GAIN_MUSCLE,
    INCREASE_STRENGTH,
    GENERAL_FITNESS;

    /** Solo estas dos usan target_weight_kg. */
    public boolean usesTargetWeight() {
        return this == LOSE_WEIGHT || this == GAIN_MUSCLE;
    }
}
