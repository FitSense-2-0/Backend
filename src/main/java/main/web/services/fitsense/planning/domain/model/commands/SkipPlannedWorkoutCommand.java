package main.web.services.fitsense.planning.domain.model.commands;

import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

/**
 * Salto declarado por el usuario. La causa es obligatoria: sin ella el ajuste de
 * la semana siguiente solo podria responder al porcentaje, que es justo lo que
 * el modificador de 18.3 quiere evitar.
 */
public record SkipPlannedWorkoutCommand(Long userId, Long plannedWorkoutId, SkipReason reason) {}
