package main.web.services.fitsense.planning.domain.model.commands;

import java.time.LocalDate;

/** Cierra la semana: el plan activo pasa a COMPLETED si nadie lo reemplazo. */
public record ClosePlanWeekCommand(Long userId, LocalDate weekStartDate) {}
