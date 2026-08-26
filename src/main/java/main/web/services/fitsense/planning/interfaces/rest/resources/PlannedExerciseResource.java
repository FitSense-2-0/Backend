package main.web.services.fitsense.planning.interfaces.rest.resources;

import java.math.BigDecimal;

public record PlannedExerciseResource(
        Long plannedExerciseId,
        Long exerciseId,
        String exerciseName,
        short exerciseOrder,
        String prescriptionType,
        Short plannedSets,
        Short plannedReps,
        Integer plannedDurationSeconds,
        BigDecimal targetLoadKg,
        Short restSeconds,
        String notes
) {}
