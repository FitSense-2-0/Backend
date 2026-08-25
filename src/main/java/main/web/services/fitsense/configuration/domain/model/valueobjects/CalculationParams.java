package main.web.services.fitsense.configuration.domain.model.valueobjects;

import java.util.List;

/**
 * Vista tipada del JSONB de calculation_configs.params.
 * <p>
 * Ninguna constante de calculo vive en el codigo Java: analytics y adaptation
 * leen de aqui. Calibrar tras el piloto es insertar una fila nueva y marcarla
 * activa, no recompilar.
 * <p>
 * El mapeo es snake_case (ver JsonSupport). Si se agrega una clave al JSON y no
 * a este record, se ignora; si se agrega aqui y falta en el JSON, llega null y
 * requireX falla de forma explicita al arrancar.
 */
public record CalculationParams(
        Adherence adherence,
        Adjustment adjustment,
        Risk risk,
        Dropout dropout
) {

    public record Adherence(
            Double sessionCompletedThresholdPct,
            Double sessionValidThresholdPct,
            Double exerciseCompletedThresholdPct,
            Double completionCapPct,
            String primaryMetric,
            String validAttemptRule
    ) {}

    public record Adjustment(
            Double goodThresholdPct,
            Double moderateThresholdPct,
            Double moderateVolumeReductionPct,
            Double lowVolumeReductionPct,
            Double fatigueExtraReductionPct,
            Double progressionIncreasePct,
            Double volumeTolerancePct,
            Double loadReductionPct,
            Double maxLoadReductionPct,
            Double maxCumulativeVolumeReductionPct,
            Double recoveryStepPct,
            Integer lowDaysReduction,
            Integer minDaysPerWeek,
            Integer minSessionMinutes,
            Integer minExercisesPerSession,
            Integer minSetsPerExercise,
            Integer minRepsPerSet,
            Integer minDurationSeconds,
            Integer durationToRepsDivisor
    ) {}

    public record Risk(
            List<AdherencePoints> adherencePoints,
            List<SkipPoints> consecutiveSkipsPoints,
            List<DropPoints> dropPoints,
            Integer overexertionPoints,
            Double rpeThreshold,
            Levels levels
    ) {
        public record AdherencePoints(Double minPct, Integer points) {}
        public record SkipPoints(Integer minCount, Integer points) {}
        public record DropPoints(Double minDropPp, Integer points) {}
        public record Levels(Double low, Double moderate, Double high, Double critical) {}
    }

    public record Dropout(Integer daysWithoutWorkout) {}

    /** Falla al arrancar, no en la primera semana de cierre. */
    public void requireComplete() {
        require(adherence, "adherence");
        require(adjustment, "adjustment");
        require(risk, "risk");
        require(dropout, "dropout");
        require(risk.levels(), "risk.levels");
        require(risk.adherencePoints(), "risk.adherence_points");
        require(adjustment.goodThresholdPct(), "adjustment.good_threshold_pct");
        require(adjustment.moderateThresholdPct(), "adjustment.moderate_threshold_pct");
        require(adherence.sessionCompletedThresholdPct(), "adherence.session_completed_threshold_pct");
        require(dropout.daysWithoutWorkout(), "dropout.days_without_workout");
    }

    private static void require(Object value, String path) {
        if (value == null)
            throw new IllegalStateException(
                    "calculation_configs.params esta incompleto: falta '%s'.".formatted(path));
    }
}
