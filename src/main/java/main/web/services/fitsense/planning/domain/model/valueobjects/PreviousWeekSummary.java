package main.web.services.fitsense.planning.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El bloque "previous_week" de 19.1. Es lo que hace posible el ajuste fino: la
 * IA recibe que se prescribio exactamente y que HIZO la persona en CADA
 * ejercicio, asi puede bajar repeticiones donde el cumplimiento fue parcial y
 * sustituir lo que se omitio, en vez de recortar a ciegas.
 * <p>
 * Hasta P-1.1 solo viajaba lo prescrito, con completion_pct siempre null
 * (hallazgo Q): el generador no podia distinguir un ejercicio cumplido de uno
 * abandonado a la mitad.
 */
public record PreviousWeekSummary(
        BigDecimal weightedAdherencePct,
        BigDecimal averageSessionRpe,
        Integer totalVolume,
        Map<String, Integer> bodyPartDistribution,
        List<WorkoutOutcome> workouts,
        List<PrescriptionOutcome> prescriptions,
        List<Long> exerciseIdsUsedLast7Days
) {
    /**
     * Un dia de entrenamiento. recorded = false significa que no hay sesion que
     * cuente: no se registro, que NO es lo mismo que haberlo hecho al 0 %.
     */
    public record WorkoutOutcome(
            LocalDate scheduledDate,
            String focusCode,
            String workoutStatus,
            /** Motivo si se salto el dia completo. */
            String skipReason,
            boolean recorded,
            String sessionStatus,
            /** Por dia: la persona lo reporta al terminar cada sesion. */
            Short sessionRpe,
            BigDecimal completionPct
    ) {}

    /**
     * Lo prescrito (sets, reps, durationSeconds, loadKg) junto a lo hecho
     * (actual*). loadKg es la carga PRESCRITA: V11 la usa para comparar.
     * <p>
     * exerciseStatus: COMPLETED, PARTIAL, SKIPPED, o NOT_RECORDED cuando no hay
     * dato de ese ejercicio.
     */
    public record PrescriptionOutcome(
            LocalDate scheduledDate,
            Long exerciseId,
            String name,
            String prescriptionType,
            Short sets,
            Short reps,
            Integer durationSeconds,
            BigDecimal loadKg,
            Short actualSets,
            Integer actualRepsTotal,
            Integer actualDurationSeconds,
            BigDecimal actualLoadKg,
            BigDecimal completionPct,
            String exerciseStatus,
            String skipReason,
            String status
    ) {}

    public static final String NOT_RECORDED = "NOT_RECORDED";

    /**
     * Referencia para el peso SUGERIDO de un ejercicio: el ultimo peso que la
     * persona anoto (dia mas reciente). El peso es solo una sugerencia para el
     * participante: no entra en la adherencia, el volumen ni el analisis.
     *
     * @param fullyCompleted hizo TODAS las repeticiones pedidas, no el 80 % de
     *                       "completado": subir peso exige haber hecho todo.
     */
    public record LoadReference(BigDecimal loadKg, boolean fullyCompleted,
                                Short sets, Short reps) {}

    public Optional<LoadReference> lastLoadFor(Long exerciseId) {
        if (prescriptions == null || exerciseId == null) return Optional.empty();
        return prescriptions.stream()
                .filter(outcome -> exerciseId.equals(outcome.exerciseId()))
                .filter(outcome -> outcome.actualLoadKg() != null)
                .filter(outcome -> outcome.actualLoadKg().signum() > 0)
                .max(Comparator.comparing(PrescriptionOutcome::scheduledDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(outcome -> new LoadReference(outcome.actualLoadKg(),
                        isFullyCompleted(outcome), outcome.sets(), outcome.reps()));
    }

    private static boolean isFullyCompleted(PrescriptionOutcome outcome) {
        if (outcome.actualRepsTotal() != null && outcome.sets() != null && outcome.reps() != null)
            return outcome.actualRepsTotal() >= outcome.sets() * outcome.reps();
        return outcome.completionPct() != null
                && outcome.completionPct().compareTo(BigDecimal.valueOf(100)) >= 0;
    }

    public static PreviousWeekSummary empty() {
        return new PreviousWeekSummary(null, null, null, Map.of(), List.of(), List.of(), List.of());
    }

    public boolean exists() {
        return totalVolume != null;
    }
}