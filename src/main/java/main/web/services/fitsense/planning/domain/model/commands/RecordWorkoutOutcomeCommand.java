package main.web.services.fitsense.planning.domain.model.commands;

import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

/**
 * Refleja en la planificacion el desenlace que determino execution. Planning no
 * calcula el porcentaje: solo registra si quedo completo, parcial o saltado.
 */
public record RecordWorkoutOutcomeCommand(
        Long plannedWorkoutId,
        WorkoutStatus outcome,
        SkipReason skipReason
) {}
