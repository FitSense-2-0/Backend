package main.web.services.fitsense.execution.interfaces.rest.resources;

import java.math.BigDecimal;

public record SessionExerciseResource(
        Long sessionExerciseId,
        Long plannedExerciseId,
        Short actualSets,
        Integer actualRepsTotal,
        Integer actualDurationSeconds,
        BigDecimal actualLoadKg,
        BigDecimal completionPercentage,
        String status,
        String skipReason
) {}
