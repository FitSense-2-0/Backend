package main.web.services.fitsense.configuration.domain.model.valueobjects;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        Dropout dropout,
        PrescriptionParams prescription
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

        /**
         * Las claves del JSON estan en MAYUSCULAS (LOW, MODERATE, HIGH,
         * CRITICAL) porque coinciden con los valores del enum RiskLevel, no con
         * nombres de campo. La estrategia snake_case del mapper no las alcanza,
         * asi que se declaran explicitas.
         * <p>
         * Sin esto los cuatro cortes llegan null, levelOf no encuentra ninguno
         * y TODA metrica sale LOW, incluso con adherencia del 5 %.
         */
        public record Levels(
                @JsonProperty("LOW") Double low,
                @JsonProperty("MODERATE") Double moderate,
                @JsonProperty("HIGH") Double high,
                @JsonProperty("CRITICAL") Double critical) {}
    }

    public record Dropout(Integer daysWithoutWorkout) {}

    /**
     * Falla al arrancar, no en la primera semana de cierre.
     * <p>
     * Comprueba tambien los cortes de riesgo uno a uno: que el objeto levels
     * exista no garantiza que sus campos hayan mapeado, y un corte null no da
     * error, solo devuelve LOW en silencio durante todo el piloto.
     */
    public void requireComplete() {
        require(adherence, "adherence");
        require(adjustment, "adjustment");
        require(risk, "risk");
        require(dropout, "dropout");
        require(risk.levels(), "risk.levels");
        require(risk.levels().moderate(), "risk.levels.MODERATE");
        require(risk.levels().high(), "risk.levels.HIGH");
        require(risk.levels().critical(), "risk.levels.CRITICAL");
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