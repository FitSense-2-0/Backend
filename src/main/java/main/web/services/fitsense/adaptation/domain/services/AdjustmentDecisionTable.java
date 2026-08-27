package main.web.services.fitsense.adaptation.domain.services;

import main.web.services.fitsense.adaptation.domain.model.valueobjects.AdjustmentContext;
import main.web.services.fitsense.adaptation.domain.model.valueobjects.AdjustmentDecision;
import main.web.services.fitsense.adaptation.domain.model.valueobjects.AdjustmentType;
import main.web.services.fitsense.configuration.domain.model.valueobjects.CalculationParams;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * La tabla de decision de 18.2, 18.3 y 18.4. Es el nucleo de la hipotesis del
 * estudio: dada la misma adherencia y la misma causa debe producir siempre la
 * misma orden, hoy y al reanalizar los datos.
 * <p>
 * Ninguna constante vive aqui: todos los umbrales vienen de calculation_configs,
 * asi que calibrar tras el piloto es insertar una fila nueva, no recompilar.
 * <p>
 * La adherencia decide CUANTO se ajusta; la causa dominante decide COMO. Reducir
 * volumen a quien no tuvo tiempo no resuelve nada: su problema es la duracion.
 * Esa distincion es justamente lo que el estudio pone a prueba.
 */
@Service
public class AdjustmentDecisionTable {

    public AdjustmentDecision decide(AdjustmentContext context, CalculationParams params) {
        var limits = params.adjustment();
        double adherence = context.weightedAdherencePct() == null
                ? 0.0 : context.weightedAdherencePct().doubleValue();

        var types = new LinkedHashSet<AdjustmentType>();
        Integer forcedDays = null;
        Integer forcedMinutes = null;
        Integer forcedDifficulty = null;
        double loadChange = 0.0;

        // ---- 18.2: la adherencia decide direccion y magnitud base.
        double volumeChangePct;
        if (adherence >= limits.goodThresholdPct()) {
            volumeChangePct = progressionFor(context, limits);
        } else if (adherence >= limits.moderateThresholdPct()) {
            volumeChangePct = -limits.moderateVolumeReductionPct();
            types.add(AdjustmentType.REDUCE_VOLUME);
        } else {
            volumeChangePct = -limits.lowVolumeReductionPct();
            types.add(AdjustmentType.REDUCE_VOLUME);
            types.add(AdjustmentType.REDUCE_DAYS);
            forcedDays = context.previousDaysPerWeek() - limits.lowDaysReduction();
        }

        // ---- 18.3: la causa dominante modifica la forma.
        var reason = context.dominantSkipReason();
        if (reason != null) {
            switch (reason) {
                case "FATIGUE" -> {
                    // Intensifica la reduccion de volumen y agrega LOWER_LOAD.
                    volumeChangePct -= limits.fatigueExtraReductionPct();
                    types.add(AdjustmentType.REDUCE_VOLUME);
                    types.add(AdjustmentType.LOWER_LOAD);
                    loadChange = -limits.loadReductionPct();
                }
                case "LACK_OF_TIME", "LACK_OF_MOTIVATION" -> {
                    // SUSTITUYE REDUCE_DAYS por REDUCE_DURATION: a quien le falta
                    // tiempo o animo hay que acortarle la sesion, no quitarle el
                    // dia, que es lo que rompe el habito.
                    types.remove(AdjustmentType.REDUCE_DAYS);
                    forcedDays = null;
                    types.add(AdjustmentType.REDUCE_DURATION);
                    forcedMinutes = shorterSession(context, limits, volumeChangePct);
                }
                case "TOO_DIFFICULT" -> {
                    types.add(AdjustmentType.LOWER_DIFFICULTY);
                    types.add(AdjustmentType.LOWER_LOAD);
                    forcedDifficulty = Math.max(1, context.previousMaxDifficulty() - 1);
                    loadChange = -limits.loadReductionPct();
                }
                case "PAIN_OR_DISCOMFORT" -> {
                    types.add(AdjustmentType.LOWER_DIFFICULTY);
                    types.add(AdjustmentType.LOWER_LOAD);
                    forcedDifficulty = Math.max(1, context.previousMaxDifficulty() - 1);
                    loadChange = -limits.maxLoadReductionPct();
                }
                default -> {
                    // SCHEDULE_CHANGE, OTHER y null no indican una dimension
                    // concreta que corregir: se queda el ajuste de volumen.
                }
            }
        }

        // ---- 18.4: topes y pisos.
        loadChange = Math.max(loadChange, -limits.maxLoadReductionPct());
        if (forcedDays != null) forcedDays = Math.max(forcedDays, limits.minDaysPerWeek());
        if (forcedMinutes != null) forcedMinutes = Math.max(forcedMinutes, limits.minSessionMinutes());

        int targetVolume = boundedTargetVolume(context, limits, volumeChangePct);
        double actualChangePct = context.previousWeekVolume() <= 0 ? 0.0
                : round((targetVolume - context.previousWeekVolume()) * 100.0
                / context.previousWeekVolume());

        double tolerance = limits.volumeTolerancePct() / 100.0;
        int min = (int) Math.floor(targetVolume * (1 - tolerance));
        int max = (int) Math.ceil(targetVolume * (1 + tolerance));

        var finalTypes = normalize(types);
        return new AdjustmentDecision(finalTypes, targetVolume, min, max, actualChangePct,
                round(loadChange), forcedDays, forcedMinutes, forcedDifficulty,
                "adherencia de %.1f %% en la semana anterior".formatted(adherence),
                distributionHint(finalTypes),
                message(adherence, reason, finalTypes, actualChangePct));
    }

    // ------------------------------------------------------------------ volumen

    /**
     * Progresion con adherencia buena (18.2 y 18.4).
     * <p>
     * Si el participante viene de reducciones, la semana siguiente RECUPERA en
     * tramos del 10 %, no de golpe: volver de un -40 % al volumen original en
     * una semana es justo el salto que provoco la caida. Solo cuando ya esta en
     * su linea base se aplica la progresion del +5 %.
     */
    private double progressionFor(AdjustmentContext context, CalculationParams.Adjustment limits) {
        boolean belowBaseline = context.baselineWeekVolume() > 0
                && context.previousWeekVolume() < context.baselineWeekVolume();
        return belowBaseline ? limits.recoveryStepPct() : limits.progressionIncreasePct();
    }

    /**
     * Aplica el cambio y luego los dos topes: nunca por debajo del 60 % del
     * volumen de la semana 1 (reduccion acumulada maxima del 40 %) ni por encima
     * de la linea base cuando se esta recuperando.
     */
    private int boundedTargetVolume(AdjustmentContext context,
                                    CalculationParams.Adjustment limits,
                                    double volumeChangePct) {
        int previous = context.previousWeekVolume();
        if (previous <= 0) return 0;

        int target = (int) Math.round(previous * (1 + volumeChangePct / 100.0));

        int baseline = context.baselineWeekVolume();
        if (baseline > 0) {
            int floor = (int) Math.round(baseline
                    * (1 - limits.maxCumulativeVolumeReductionPct() / 100.0));
            target = Math.max(target, floor);

            // Al recuperar no se pasa de la linea base: la progresion por encima
            // es otra decision, y se toma la semana siguiente.
            if (previous < baseline) target = Math.min(target, baseline);
        }

        return Math.max(1, target);
    }

    private Integer shorterSession(AdjustmentContext context, CalculationParams.Adjustment limits,
                                   double volumeChangePct) {
        double factor = 1 + Math.min(0.0, volumeChangePct) / 100.0;
        return (int) Math.max(limits.minSessionMinutes(),
                Math.round(context.previousSessionMinutes() * factor));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * ck_ui_types_size admite entre uno y cuatro tipos, y ck_ui_none_alone exige
     * que NONE vaya solo. Recortar aqui evita que la base rechace la fila en el
     * cierre semanal, que es el peor momento para fallar.
     */
    private List<AdjustmentType> normalize(LinkedHashSet<AdjustmentType> types) {
        var ordered = new ArrayList<>(types);
        ordered.remove(AdjustmentType.NONE);
        if (ordered.isEmpty()) return List.of(AdjustmentType.NONE);
        return ordered.size() <= 4 ? List.copyOf(ordered) : List.copyOf(ordered.subList(0, 4));
    }

    /** Pista para la IA. El motor de reglas la ignora: su reparto ya es fijo. */
    private String distributionHint(List<AdjustmentType> types) {
        if (types.size() == 1 && types.get(0) == AdjustmentType.NONE) return null;
        return "Prioriza bajar repeticiones, series y carga antes que quitar ejercicios.";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** El mensaje que ve el participante. Explica el porque, no solo el que. */
    private String message(double adherence, String reason, List<AdjustmentType> types,
                           double changePct) {

        if (types.size() == 1 && types.get(0) == AdjustmentType.NONE) {
            if (changePct > 0)
                return ("Cumpliste el %.0f %% de tu plan. Esta semana subimos un poco el volumen "
                        + "para seguir avanzando.").formatted(adherence);
            return "Cumpliste el %.0f %% de tu plan. Mantenemos el mismo volumen esta semana."
                    .formatted(adherence);
        }

        var causa = switch (String.valueOf(reason)) {
            case "FATIGUE" -> "Nos dijiste que llegabas cansado, asi que bajamos el volumen y la carga.";
            case "LACK_OF_TIME" -> "Nos dijiste que te falto tiempo, asi que acortamos las sesiones "
                    + "y mantenemos los mismos dias.";
            case "LACK_OF_MOTIVATION" -> "Acortamos las sesiones para que sea mas facil retomar el ritmo.";
            case "TOO_DIFFICULT" -> "Nos dijiste que resultaba dificil, asi que bajamos la exigencia.";
            case "PAIN_OR_DISCOMFORT" -> "Reportaste molestias, asi que bajamos exigencia y carga. "
                    + "Si el dolor sigue, consulta a un profesional.";
            case "SCHEDULE_CHANGE" -> "Ajustamos el plan a lo que pudiste sostener esta semana.";
            default -> "Ajustamos el plan a lo que lograste esta semana.";
        };

        return "Cumpliste el %.0f %% de tu plan. %s".formatted(adherence, causa);
    }
}
