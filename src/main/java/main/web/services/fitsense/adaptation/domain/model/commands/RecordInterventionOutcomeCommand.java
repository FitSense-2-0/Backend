package main.web.services.fitsense.adaptation.domain.model.commands;

import java.math.BigDecimal;

/**
 * Cierra el circulo de la intervencion de la semana anterior: dice si el ajuste
 * mejoro la adherencia. Es el paso 4 de la tarea semanal de la seccion 22 y lo
 * que convierte user_interventions en una tabla con resultado, no solo con
 * intencion.
 */
public record RecordInterventionOutcomeCommand(Long userId, BigDecimal adherenceAfterPct) {}
