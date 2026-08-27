package main.web.services.fitsense.analytics.interfaces.acl;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que adaptation necesita para decidir el ajuste: el porcentaje que dispara
 * la regla y la causa que la modula.
 */
public record WeeklyMetricsView(
        Long weeklyMetricId,
        Long planId,
        short weekNumber,
        LocalDate weekStartDate,
        boolean hasActivePlan,
        BigDecimal weightedAdherencePct,
        BigDecimal averageSessionRpe,
        int consecutiveSkips,
        String dominantSkipReason,
        String riskLevel,
        boolean dropout,
        int scheduledWorkouts,
        int assignedExercises
) {
    /** Sin plan o sin adherencia calculable no hay nada que ajustar. */
    public boolean isActionable() {
        return hasActivePlan && weightedAdherencePct != null && planId != null;
    }
}
