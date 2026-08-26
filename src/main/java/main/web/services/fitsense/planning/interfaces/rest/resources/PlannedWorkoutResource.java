package main.web.services.fitsense.planning.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PlannedWorkoutResource(
        Long plannedWorkoutId,
        LocalDate scheduledDate,
        String focusCode,
        String workoutName,
        short expectedDurationMinutes,
        short displayOrder,
        String status,
        String skipReason,
        OffsetDateTime expiresAt,
        List<PlannedExerciseResource> exercises
) {}
