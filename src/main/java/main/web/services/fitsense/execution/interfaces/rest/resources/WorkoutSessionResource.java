package main.web.services.fitsense.execution.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record WorkoutSessionResource(
        Long sessionId,
        Long plannedWorkoutId,
        Long planId,
        short attemptNumber,
        boolean countsTowardAdherence,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Short activeMinutes,
        BigDecimal completionPercentage,
        Short sessionRpe,
        Short satisfaction,
        String source,
        String status,
        List<SessionExerciseResource> exercises
) {}
