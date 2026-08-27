package main.web.services.fitsense.execution.domain.model.commands;

public record AbandonWorkoutSessionCommand(Long userId, Long sessionId) {}
