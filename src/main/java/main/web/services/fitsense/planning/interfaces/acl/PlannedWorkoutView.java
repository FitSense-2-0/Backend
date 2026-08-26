package main.web.services.fitsense.planning.interfaces.acl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Vista de un entrenamiento indicado para execution. */
public record PlannedWorkoutView(
        Long plannedWorkoutId,
        Long planId,
        Long userId,
        LocalDate scheduledDate,
        String focusCode,
        String workoutName,
        short expectedDurationMinutes,
        String status,
        OffsetDateTime expiresAt,
        List<PlannedExerciseTarget> exercises
) {
    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean isOpen() {
        return "SCHEDULED".equals(status) || "IN_PROGRESS".equals(status);
    }
}
