package main.web.services.fitsense.analytics.domain.model.commands;

import java.time.LocalDate;

/**
 * Cierra la semana de un participante. Es idempotente: volver a ejecutarlo
 * sobrescribe la fila en vez de duplicarla, lo que permite recalcular tras
 * cambiar la version de umbrales.
 */
public record CalculateWeeklyMetricsCommand(Long userId, LocalDate weekStartDate) {}
