package main.web.services.fitsense.analytics.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resultado del calculo semanal antes de persistirse.
 * <p>
 * Los porcentajes son nullables a proposito: NULL significa "no hubo
 * denominador", y ese matiz separa a un participante que no cumplio de uno al
 * que el sistema no llego a proponerle nada. ck_wum_null_when_no_plan lo exige
 * en la base.
 */
public record MetricsCalculation(
        boolean hasActivePlan,
        Long planId,
        short weekNumber,
        int scheduledWorkouts,
        int validWorkouts,
        int completedWorkouts,
        int skippedWorkouts,
        int assignedExercises,
        int completedExercises,
        BigDecimal weightedAdherencePct,
        BigDecimal frequencyAdherencePct,
        BigDecimal workoutAdherencePct,
        BigDecimal exerciseAdherencePct,
        int totalTrainingMinutes,
        BigDecimal averageSessionRpe,
        BigDecimal averageSatisfaction,
        int consecutiveSkips,
        Short daysSinceLastWorkout,
        String dominantSkipReason,
        BigDecimal riskScore,
        RiskLevel riskLevel,
        Map<String, Object> riskFactors,
        boolean dropout
) {}
