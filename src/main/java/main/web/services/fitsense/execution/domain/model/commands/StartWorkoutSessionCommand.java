package main.web.services.fitsense.execution.domain.model.commands;

public record StartWorkoutSessionCommand(Long userId, Long plannedWorkoutId) {}
