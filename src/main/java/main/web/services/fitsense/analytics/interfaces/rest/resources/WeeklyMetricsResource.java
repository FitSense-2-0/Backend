package main.web.services.fitsense.analytics.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WeeklyMetricsResource(
        Long weeklyMetricId,
        Long planId,
        short weekNumber,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        boolean hasActivePlan,
        short scheduledWorkouts,
        short validWorkouts,
        short completedWorkouts,
        short skippedWorkouts,
        short assignedExercises,
        short completedExercises,
        BigDecimal weightedAdherencePct,
        BigDecimal frequencyAdherencePct,
        BigDecimal workoutAdherencePct,
        BigDecimal exerciseAdherencePct,
        int totalTrainingMinutes,
        BigDecimal averageSessionRpe,
        BigDecimal averageSatisfaction,
        short consecutiveSkips,
        Short daysSinceLastWorkout,
        String dominantSkipReason,
        BigDecimal riskScore,
        String riskLevel,
        boolean dropout,
        String calculationVersion,
        OffsetDateTime calculatedAt
) {}
