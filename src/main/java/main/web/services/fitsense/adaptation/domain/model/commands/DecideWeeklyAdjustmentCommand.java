package main.web.services.fitsense.adaptation.domain.model.commands;

import java.time.LocalDate;

/**
 * Decide el ajuste de la semana siguiente a partir de las metricas ya cerradas.
 * Idempotente por uq_ui_metric: si ya hay intervencion para esa semana medida,
 * se devuelve la existente en vez de crear otra.
 */
public record DecideWeeklyAdjustmentCommand(Long userId, LocalDate measuredWeekStartDate) {}
