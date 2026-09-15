package main.web.services.fitsense.adaptation.domain.model.commands;

import java.math.BigDecimal;

/**
 * Cierra el circulo de la intervencion que produjo el plan medido: dice si el
 * ajuste mejoro la adherencia. Es el paso 3 de la tarea semanal de la seccion 22
 * y lo que convierte user_interventions en una tabla con resultado, no solo con
 * intencion.
 *
 * @param measuredPlanId el plan de la semana que se acaba de medir. Identifica
 *                       sin ambiguedad a que intervencion pertenece este
 *                       resultado. Antes se buscaba "la ultima intervencion del
 *                       usuario", y bastaba una semana sin plan para que el
 *                       resultado se escribiera sobre una orden ajena.
 */
public record RecordInterventionOutcomeCommand(Long measuredPlanId, BigDecimal adherenceAfterPct) {}