package main.web.services.fitsense.planning.interfaces.rest.resources;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WeeklyTrainingPlanResource(
        Long planId,
        short weekNumber,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        short planVersion,
        Long parentPlanId,
        short plannedDaysCount,
        String status,
        String generationSource,
        String modelName,
        String adjustmentApplied,
        String adjustmentReason,
        OffsetDateTime activatedAt,
        List<PlannedWorkoutResource> workouts
) {}
