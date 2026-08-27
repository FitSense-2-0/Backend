package main.web.services.fitsense.execution.domain.model.commands;

public record FinishWorkoutSessionCommand(
        Long userId,
        Long sessionId,
        Short sessionRpe,
        Short satisfaction,
        Short activeMinutes
) {}
