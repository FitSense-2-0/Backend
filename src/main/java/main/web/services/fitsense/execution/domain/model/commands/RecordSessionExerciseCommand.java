package main.web.services.fitsense.execution.domain.model.commands;

import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

import java.math.BigDecimal;

public record RecordSessionExerciseCommand(
        Long userId,
        Long sessionId,
        Long plannedExerciseId,
        Short actualSets,
        Integer actualRepsTotal,
        Integer actualDurationSeconds,
        BigDecimal actualLoadKg,
        SkipReason skipReason
) {}
