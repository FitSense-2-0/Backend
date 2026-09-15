package main.web.services.fitsense.planning.domain.model.valueobjects;

import main.web.services.fitsense.planning.domain.model.commands.GenerateWeeklyPlanCommand;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Lo que sobrevive entre la lectura del contexto y la persistencia del plan.
 * <p>
 * Existe porque la generacion tarda ~90 s de HTTP y NO puede ocurrir dentro de
 * una transaccion: Postgres termina la conexion por idle-in-transaction y se
 * pierden el cierre de semana y las metricas ya calculadas en ella. Con 30
 * participantes agota ademas el pool de HikariCP.
 * <p>
 * Lleva el ID del plan previo, no la entidad: entre fase y fase no hay sesion
 * de persistencia abierta y la entidad quedaria desasociada.
 * <p>
 * El ajuste y las fechas van sueltos en vez de leerse de context para que la
 * fase 3 no dependa de la forma interna de PlanGenerationContext.
 */
public record PreparedPlanGeneration(
        GenerateWeeklyPlanCommand command,
        PlanGenerationContext context,
        PlanAdjustment adjustment,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        int divisor,
        short weekNumber,
        Long previousActivePlanId,
        ZoneId zone
) {}