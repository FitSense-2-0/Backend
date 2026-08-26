package main.web.services.fitsense.planning.interfaces.rest.resources;

import java.time.LocalDate;

/** Fila del historial. Sin los entrenamientos: la lista completa se pide por id. */
public record PlanSummaryResource(
        Long planId,
        short weekNumber,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        short planVersion,
        short plannedDaysCount,
        String status,
        String generationSource,
        String adjustmentApplied
) {}
