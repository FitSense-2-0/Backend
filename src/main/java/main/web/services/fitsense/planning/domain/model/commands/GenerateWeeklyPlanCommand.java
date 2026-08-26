package main.web.services.fitsense.planning.domain.model.commands;

import main.web.services.fitsense.planning.domain.model.valueobjects.PlanAdjustment;

import java.time.LocalDate;

/**
 * Genera el plan de una semana. Si adjustment es null se usa la linea base sin
 * ajuste, que es lo que ocurre en la primera semana del participante.
 * <p>
 * replaceExisting distingue los dos caminos legitimos: la generacion del lunes
 * (false, no debe haber plan activo) y el reemplazo por intervencion (true,
 * versiona el plan vigente).
 */
public record GenerateWeeklyPlanCommand(
        Long userId,
        LocalDate weekStartDate,
        PlanAdjustment adjustment,
        boolean replaceExisting
) {
    public static GenerateWeeklyPlanCommand firstPlan(Long userId, LocalDate weekStartDate) {
        return new GenerateWeeklyPlanCommand(userId, weekStartDate, null, false);
    }
}
