package main.web.services.fitsense.planning.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * El bloque "previous_week" de 19.1. Es lo que hace posible el ajuste fino: la
 * IA recibe que se prescribio exactamente y cuanto se cumplio de CADA ejercicio,
 * asi puede bajar repeticiones donde el cumplimiento fue parcial y sustituir lo
 * que se omitio por completo, en vez de recortar a ciegas.
 */
public record PreviousWeekSummary(
        BigDecimal weightedAdherencePct,
        BigDecimal averageSessionRpe,
        Integer totalVolume,
        Map<String, Integer> bodyPartDistribution,
        List<PrescriptionOutcome> prescriptions,
        List<Long> exerciseIdsUsedLast7Days
) {
    public record PrescriptionOutcome(
            Long exerciseId,
            String name,
            Short sets,
            Short reps,
            BigDecimal loadKg,
            BigDecimal completionPct,
            String status
    ) {}

    public static PreviousWeekSummary empty() {
        return new PreviousWeekSummary(null, null, null, Map.of(), List.of(), List.of());
    }

    public boolean exists() {
        return totalVolume != null;
    }
}
