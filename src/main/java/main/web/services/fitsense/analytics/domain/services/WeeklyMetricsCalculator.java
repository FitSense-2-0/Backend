package main.web.services.fitsense.analytics.domain.services;

import main.web.services.fitsense.analytics.domain.model.valueobjects.*;
import main.web.services.fitsense.configuration.domain.model.valueobjects.CalculationParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Las cuatro metricas de 17.3 y el riesgo. Servicio de dominio puro: recibe el
 * denominador, las sesiones y los umbrales, y no consulta nada.
 * <p>
 * Que sea puro no es purismo: es lo que permite recalcular una semana meses
 * despues con otra version de umbrales y comparar, que es exactamente lo que
 * pide la calibracion posterior al piloto.
 */
@Service
public class WeeklyMetricsCalculator {

    public MetricsCalculation calculate(WeekPlanInput plan,
                                        List<WeekSessionInput> sessions,
                                        BigDecimal previousWeightedAdherencePct,
                                        Short daysSinceLastWorkout,
                                        CalculationParams params) {

        if (!plan.hasActivePlan()) return withoutPlan(plan, daysSinceLastWorkout, params);

        // 17.4 punto 3: por cada entrenamiento, UNA sesion. El repositorio ya
        // filtra por counts_toward_adherence, asi que aqui solo se indexa.
        var byWorkout = sessions.stream().collect(Collectors.toMap(
                WeekSessionInput::plannedWorkoutId, session -> session, (a, b) -> b));

        var adherence = params.adherence();
        int scheduled = plan.scheduledWorkouts();

        int valid = 0;
        int completed = 0;
        int skipped = 0;
        int completedExercises = 0;
        int trainingMinutes = 0;
        double weightedNumerator = 0.0;
        double weightedDenominator = 0.0;

        var rpes = new ArrayList<Short>();
        var satisfactions = new ArrayList<Short>();
        var skipReasons = new ArrayList<String>();

        for (var workout : plan.workouts()) {
            var session = byWorkout.get(workout.plannedWorkoutId());
            double completion = session == null ? 0.0 : session.completion();

            // El peso es la duracion INDICADA, no la real: una sesion de 60
            // minutos no vale lo mismo que una de 20 al medir cuanto del plan se
            // cumplio, y usar la real premiaria alargar sesiones facilies.
            weightedDenominator += workout.expectedDurationMinutes();
            weightedNumerator += workout.expectedDurationMinutes() * completion;

            if (session != null) {
                if (completion >= adherence.sessionValidThresholdPct()) valid++;
                else skipped++;
                if (completion >= adherence.sessionCompletedThresholdPct()) completed++;
                completedExercises += session.completedExercises();
                if (session.activeMinutes() != null) trainingMinutes += session.activeMinutes();
                if (session.sessionRpe() != null) rpes.add(session.sessionRpe());
                if (session.satisfaction() != null) satisfactions.add(session.satisfaction());
                if (session.dominantSkipReason() != null) skipReasons.add(session.dominantSkipReason());
            } else {
                skipped++;
            }

            if (workout.skipReason() != null) skipReasons.add(workout.skipReason());
        }

        var weighted = weightedDenominator <= 0 ? null
                : percentage(weightedNumerator / weightedDenominator);
        var frequency = percentage(ratio(valid, scheduled) * 100);
        var workoutAdherence = percentage(ratio(completed, scheduled) * 100);
        var exerciseAdherence = plan.assignedExercises() <= 0 ? null
                : percentage(ratio(completedExercises, plan.assignedExercises()) * 100);

        int consecutiveSkips = trailingSkipStreak(plan, byWorkout, adherence.sessionValidThresholdPct());
        var averageRpe = average(rpes);

        var riskFactors = new LinkedHashMap<String, Object>();
        var riskScore = scoreRisk(weighted, consecutiveSkips, previousWeightedAdherencePct,
                averageRpe, params, riskFactors);

        boolean dropout = daysSinceLastWorkout != null
                && daysSinceLastWorkout >= params.dropout().daysWithoutWorkout();
        if (dropout) riskFactors.put("dropout_days", daysSinceLastWorkout);

        return new MetricsCalculation(
                true, plan.planId(), plan.weekNumber(),
                scheduled, valid, completed, skipped,
                plan.assignedExercises(), completedExercises,
                weighted, frequency, workoutAdherence, exerciseAdherence,
                trainingMinutes, averageRpe, average(satisfactions),
                consecutiveSkips, daysSinceLastWorkout, dominant(skipReasons),
                riskScore, levelOf(riskScore, params.risk().levels()), riskFactors, dropout);
    }

    /**
     * Semana sin plan. Todas las adherencias van NULL: sin denominador no hay
     * porcentaje, y un cero aqui contaminaria los promedios del estudio con
     * semanas en las que el sistema simplemente no propuso nada (19.4).
     */
    private MetricsCalculation withoutPlan(WeekPlanInput plan, Short daysSinceLastWorkout,
                                           CalculationParams params) {
        boolean dropout = daysSinceLastWorkout != null
                && daysSinceLastWorkout >= params.dropout().daysWithoutWorkout();

        var factors = new LinkedHashMap<String, Object>();
        factors.put("no_active_plan", true);
        if (dropout) factors.put("dropout_days", daysSinceLastWorkout);

        return new MetricsCalculation(false, null, plan.weekNumber(),
                0, 0, 0, 0, 0, 0, null, null, null, null,
                0, null, null, 0, daysSinceLastWorkout, null,
                null, null, factors, dropout);
    }

    // ------------------------------------------------------------------- riesgo

    private BigDecimal scoreRisk(BigDecimal weighted, int consecutiveSkips,
                                 BigDecimal previousWeighted, BigDecimal averageRpe,
                                 CalculationParams params, Map<String, Object> factors) {
        var risk = params.risk();
        int score = 0;

        if (weighted != null) {
            int points = pointsFor(risk.adherencePoints(), weighted.doubleValue(),
                    entry -> entry.minPct(), entry -> entry.points());
            if (points > 0) factors.put("low_adherence", points);
            score += points;
        }

        int skipPoints = pointsFor(risk.consecutiveSkipsPoints(), consecutiveSkips,
                entry -> (double) entry.minCount(), entry -> entry.points());
        if (skipPoints > 0) factors.put("consecutive_skips", skipPoints);
        score += skipPoints;

        if (weighted != null && previousWeighted != null) {
            double drop = previousWeighted.doubleValue() - weighted.doubleValue();
            if (drop > 0) {
                int dropPoints = pointsFor(risk.dropPoints(), drop,
                        entry -> entry.minDropPp(), entry -> entry.points());
                if (dropPoints > 0) factors.put("adherence_drop", dropPoints);
                score += dropPoints;
            }
        }

        if (averageRpe != null && risk.rpeThreshold() != null
                && averageRpe.doubleValue() >= risk.rpeThreshold()) {
            factors.put("overexertion", risk.overexertionPoints());
            score += risk.overexertionPoints();
        }

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /** Toma el tramo mas alto que el valor alcanza. Las tablas viven en la configuracion. */
    private <T> int pointsFor(List<T> table, double value,
                              java.util.function.ToDoubleFunction<T> threshold,
                              java.util.function.ToIntFunction<T> points) {
        if (table == null) return 0;
        return table.stream()
                .sorted(Comparator.comparingDouble(threshold).reversed())
                .filter(entry -> value >= threshold.applyAsDouble(entry))
                .mapToInt(points)
                .findFirst()
                .orElse(0);
    }

    private RiskLevel levelOf(BigDecimal score, CalculationParams.Risk.Levels levels) {
        if (score == null || levels == null) return null;
        double value = score.doubleValue();
        if (levels.critical() != null && value >= levels.critical()) return RiskLevel.CRITICAL;
        if (levels.high() != null && value >= levels.high()) return RiskLevel.HIGH;
        if (levels.moderate() != null && value >= levels.moderate()) return RiskLevel.MODERATE;
        return RiskLevel.LOW;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Racha de entrenamientos seguidos sin sesion valida al FINAL de la semana.
     * <p>
     * Se cuenta desde el ultimo hacia atras: tres saltos al cierre predicen
     * abandono; tres saltos al inicio con recuperacion posterior, no.
     */
    private int trailingSkipStreak(WeekPlanInput plan, Map<Long, WeekSessionInput> byWorkout,
                                   double validThreshold) {
        var ordered = plan.workouts().stream()
                .sorted(Comparator.comparing(WeekPlanInput.ScheduledWorkout::scheduledDate))
                .toList();

        int streak = 0;
        for (int i = ordered.size() - 1; i >= 0; i--) {
            var session = byWorkout.get(ordered.get(i).plannedWorkoutId());
            if (session != null && session.completion() >= validThreshold) break;
            streak++;
        }
        return streak;
    }

    private String dominant(List<String> values) {
        return values.stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private BigDecimal average(List<Short> values) {
        if (values.isEmpty()) return null;
        double sum = values.stream().mapToInt(Short::intValue).sum();
        return BigDecimal.valueOf(sum / values.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    /** MIN(x, 1) x 100 de 17.3: superar lo indicado no compensa otra sesion no hecha. */
    private BigDecimal percentage(double value) {
        return BigDecimal.valueOf(Math.max(0.0, Math.min(100.0, value)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
