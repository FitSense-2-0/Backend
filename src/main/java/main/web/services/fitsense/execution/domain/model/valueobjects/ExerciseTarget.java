package main.web.services.fitsense.execution.domain.model.valueobjects;

/**
 * Lo indicado para un ejercicio, traducido al lenguaje de execution.
 * <p>
 * Execution no lee la tabla de planning: recibe el objetivo por la capa
 * anticorrupcion. Asi el calculo de adherencia depende de un contrato explicito
 * y no de la forma interna del agregado ajeno.
 */
public record ExerciseTarget(
        Long plannedExerciseId,
        Long exerciseId,
        boolean durationBased,
        int plannedRepsTotal,
        Integer plannedDurationSeconds
) {
    /** Denominador del porcentaje de este ejercicio. */
    public int denominator() {
        if (durationBased) return plannedDurationSeconds == null ? 0 : plannedDurationSeconds;
        return plannedRepsTotal;
    }
}
