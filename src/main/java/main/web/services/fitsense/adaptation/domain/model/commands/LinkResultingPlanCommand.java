package main.web.services.fitsense.adaptation.domain.model.commands;

import java.time.LocalDate;

/**
 * Registra que plan salio del ajuste y con cuanto volumen.
 *
 * @param resultingWeekStart semana del plan enlazado. Antes el volumen se leia
 *                           de la semana que contenia la fecha de hoy, lo que
 *                           daba un numero de otra semana si el ciclo corria con
 *                           retraso o despues de un fallo.
 */
public record LinkResultingPlanCommand(Long interventionId, Long resultingPlanId,
                                       LocalDate resultingWeekStart) {}