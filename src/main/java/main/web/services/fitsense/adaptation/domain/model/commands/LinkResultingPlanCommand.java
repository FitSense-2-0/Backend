package main.web.services.fitsense.adaptation.domain.model.commands;

/** Registra que plan salio del ajuste y con cuanto volumen. */
public record LinkResultingPlanCommand(Long interventionId, Long resultingPlanId) {}
