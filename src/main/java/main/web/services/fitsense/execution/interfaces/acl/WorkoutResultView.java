package main.web.services.fitsense.execution.interfaces.acl;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que la persona HIZO en un entrenamiento, ejercicio por ejercicio, visto
 * desde fuera de execution.
 * <p>
 * Solo el intento que cuenta para la adherencia (el ultimo finalizado). Asi la
 * IA y la metrica leen exactamente los mismos datos de la misma semana.
 */
public record WorkoutResultView(
        Long plannedWorkoutId,
        String sessionStatus,
        /** 1-10, lo reporta la persona al terminar. Puede venir null. */
        Short sessionRpe,
        BigDecimal completionPercentage,
        List<ExerciseResult> exercises
) {
    /**
     * Totales, no serie por serie: no existe tabla de series. Se sabe que hizo
     * 16 de 30 repeticiones, no si fue 10+6+0 o 6+5+5.
     */
    public record ExerciseResult(
            Long plannedExerciseId,
            Short actualSets,
            Integer actualRepsTotal,
            Integer actualDurationSeconds,
            BigDecimal actualLoadKg,
            BigDecimal completionPercentage,
            String status,
            String skipReason
    ) {}
}