package main.web.services.fitsense.execution.interfaces.acl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Una sesion que cuenta para la adherencia, vista desde fuera de execution. */
public record SessionSummaryView(
        Long sessionId,
        Long plannedWorkoutId,
        Long planId,
        OffsetDateTime startedAt,
        BigDecimal completionPercentage,
        String status,
        Short activeMinutes,
        Short sessionRpe,
        Short satisfaction,
        int completedExercises,
        String dominantSkipReason
) {
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
}
