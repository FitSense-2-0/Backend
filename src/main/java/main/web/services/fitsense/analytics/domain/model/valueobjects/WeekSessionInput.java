package main.web.services.fitsense.analytics.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * Una sesion que cuenta, traducida al lenguaje de analytics.
 * <p>
 * Solo llegan las que tienen counts_toward_adherence = true: los intentos
 * distintos de un mismo entrenamiento no se suman (17.4, punto 4).
 */
public record WeekSessionInput(
        Long plannedWorkoutId,
        BigDecimal completionPercentage,
        Short activeMinutes,
        Short sessionRpe,
        Short satisfaction,
        int completedExercises,
        String dominantSkipReason
) {
    public double completion() {
        return completionPercentage == null ? 0.0 : completionPercentage.doubleValue();
    }
}
