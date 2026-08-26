package main.web.services.fitsense.planning.domain.model.commands;

/** Marca el entrenamiento como en curso cuando execution abre la sesion. */
public record StartPlannedWorkoutCommand(Long plannedWorkoutId) {}
