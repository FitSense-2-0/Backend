package main.web.services.fitsense.execution.domain.model.commands;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Ya lo hice": registra un entrenamiento completo de una sola vez, tipicamente
 * uno ya vencido. Sin este camino, entrenar sin la app abierta se registraria
 * como no entrenar y la adherencia mediria uso de la app, no ejercicio.
 */
public record ReportWorkoutCommand(
        Long userId,
        Long plannedWorkoutId,
        OffsetDateTime performedAt,
        Short sessionRpe,
        Short satisfaction,
        Short activeMinutes,
        List<ReportedExercise> exercises
) {
    public record ReportedExercise(
            Long plannedExerciseId,
            Short actualSets,
            Integer actualRepsTotal,
            Integer actualDurationSeconds,
            java.math.BigDecimal actualLoadKg,
            main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason skipReason
    ) {}
}
