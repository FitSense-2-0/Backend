package main.web.services.fitsense.planning.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Propuesta cruda del generador, antes de validarse y persistirse (formato 19.2).
 * <p>
 * Existe para que la validacion ocurra sobre datos y no sobre un agregado ya
 * construido: un borrador invalido nunca llega a tener un plan_id y por lo tanto
 * nunca puede confundirse con un plan real en el analisis.
 */
public record PlanDraft(
        GenerationSource source,
        String modelName,
        String planName,
        Integer declaredTotalVolume,
        String rationale,
        List<DraftWorkout> workouts
) {
    public static final String SCHEMA_VERSION = "GEN-OUT-1.0";

    public record DraftWorkout(
            LocalDate scheduledDate,
            WorkoutFocus focus,
            String name,
            int expectedDurationMinutes,
            List<DraftExercise> exercises
    ) {}

    public record DraftExercise(
            Long exerciseId,
            PrescriptionType prescriptionType,
            Short plannedSets,
            Short plannedReps,
            Integer plannedDurationSeconds,
            BigDecimal targetLoadKg,
            Short restSeconds,
            String notes
    ) {
        /** 18.1: sets x reps, o segundos / divisor para los de duracion. */
        public int volume(int durationToRepsDivisor) {
            if (prescriptionType == PrescriptionType.SETS_REPS)
                return (plannedSets == null || plannedReps == null) ? 0 : plannedSets * plannedReps;
            if (plannedDurationSeconds == null || durationToRepsDivisor <= 0) return 0;
            int sets = plannedSets == null ? 1 : plannedSets;
            return Math.round((float) (plannedDurationSeconds * sets) / durationToRepsDivisor);
        }
    }

    /**
     * Volumen real recalculado. NO se confia en declaredTotalVolume: la IA lo
     * declara y el backend lo verifica (19.2). Si no coincide, falla la
     * validacion 8.
     */
    public int volume(int durationToRepsDivisor) {
        return workouts.stream()
                .flatMap(workout -> workout.exercises().stream())
                .mapToInt(exercise -> exercise.volume(durationToRepsDivisor))
                .sum();
    }
}
