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

        int previous = context.previousWeekVolume();
        int delta = Math.abs(targetVolume - previous);
        double tolerance = limits.volumeTolerancePct() / 100.0;
        int pctSlack = (int) Math.round(targetVolume * tolerance);

        // La banda de 18.4 era simetrica y en porcentaje, pero el volumen solo se
        // mueve a saltos: con 16 ejercicios a 4 series el paso minimo son 4
        // unidades, un 0,96 % sobre 416, mientras la tolerancia del 5 % son 21.
        // Se ordenaba un paso con un margen de diez pasos de ancho, y dentro de
        // ese margen obedecer y desobedecer eran indistinguibles.
        //
        // REGLA 1: el margen no puede ser mas ancho que la mitad del cambio
        // ordenado, de modo que la banda nunca solapa el volumen previo. Una
        // orden de subir exige subir. Solo cuando la orden es mantener (delta 0)
        // se usa la tolerancia en porcentaje: ahi no hay movimiento que exigir y
        // el generador necesita holgura para cuadrar bloques discretos.
        int slack = delta == 0 ? pctSlack : Math.max(1, Math.min(pctSlack, delta / 2));

        int min = targetVolume - slack;
        int max = targetVolume + slack;

        // REGLA 2, direccionalidad: el volumen entregado no puede quedar por
        // debajo del previo salvo que la orden sea reducir. Cubre el caso de
        // mantener, donde la regla 1 deja la banda ancha a proposito.
        //
        // Observado sin esta regla, con 100 % de adherencia tres semanas
        // seguidas: 420 -> 416 -> 416 -> 412. El generador entregaba algo menos
        // de lo ordenado (legal), el volumen caia bajo la linea base, la regla de
        // recuperacion recortaba el objetivo a la linea base, y esa orden volvia
        // a ser menor que la tolerancia. Bucle descendente.
        if (targetVolume >= previous) min = Math.max(min, previous);
        else max = Math.min(max, previous);

        // REGLA 3: los topes absolutos se aplicaban solo al objetivo, no a la
        // banda. Con el techo en 431 el maximo aceptado salia 453, un 5 % por
        // encima del techo declarado: el techo real no era el escrito.
        min = Math.max(min, absoluteFloor(context, limits));
        int ceiling = absoluteCeiling(context, limits);
        if (ceiling != Integer.MAX_VALUE) max = Math.min(max, ceiling);

        // Red de seguridad: el objetivo siempre cae dentro de su propia banda,
        // aunque los topes la hayan recortado por un lado.
        min = Math.max(1, Math.min(min, targetVolume));
        max = Math.max(max, targetVolume);
        if (max < min) max = min;

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
     * Dos caminos distintos, y la diferencia importa:
     * <p>
     * RECUPERAR. Si el participante viene de reducciones, vuelve hacia su linea
     * base en tramos del 10 %, no de golpe: saltar de un -40 % al volumen
     * original en una semana es justo lo que provoco la caida. Esto NO exige el
     * criterio de dos semanas, porque no es sobrecarga progresiva: es restituir
     * un volumen que la persona ya sostuvo.
     * <p>
     * PROGRESAR por encima de la linea base si es sobrecarga, y ahi si se exige
     * evidencia de desempeno sostenido. El ACSM condiciona el incremento a
     * mantener el rendimiento en dos sesiones consecutivas, no a que pase una
     * semana.
     */
    private double progressionFor(AdjustmentContext context, CalculationParams.Adjustment limits) {
        boolean belowBaseline = context.baselineWeekVolume() > 0
                && context.previousWeekVolume() < context.baselineWeekVolume();

        if (belowBaseline) return limits.recoveryStepPct();

        return meetsProgressionCriteria(context, limits)
                ? limits.progressionIncreasePct()
                : 0.0;
    }

    /**
     * Condiciones para subir el volumen por encima de la linea base.
     * <p>
     * La adherencia mide que la persona HIZO lo pedido, no que le resultara
     * llevadero. Progresar solo por adherencia le sube la carga igual a quien
     * va sobrado y a quien esta al limite aguantando; al segundo lo empuja
     * exactamente hacia el abandono, y el abandono es variable de resultado del
     * estudio.
     */
    private boolean meetsProgressionCriteria(AdjustmentContext context,
                                             CalculationParams.Adjustment limits) {
        // 1. Desempeno sostenido: N semanas seguidas cumpliendo.
        if (limits.requiredWeeksOrDefault() >= 2) {
            var previous = context.previousWeekAdherencePct();
            if (previous == null || previous.doubleValue() < limits.goodThresholdPct())
                return false;
        }

        // 2. Sin RPE no se progresa. Ante dato faltante, la opcion conservadora:
        // no sabemos si le costo, asi que no le subimos la carga.
        var rpe = context.averageSessionRpe();
        if (rpe == null) return false;

        // 3. Techo de esfuerzo percibido. DESACTIVADO mientras la escala no este
        // instrumentada: session_rpe es hoy un entero 1-10 sin anclajes
        // verbales, opcional y sin familiarizacion previa, y Foster et al.
        // (2001) exigen esa familiarizacion para que la medida sea fiable.
        // Decidir la progresion con ese dato seria decidir con ruido.
        if (limits.progressionRpeCeiling() != null
                && rpe.doubleValue() > limits.progressionRpeCeiling())
            return false;

        return true;
    }

    /**
     * Aplica el cambio y luego los topes de 18.4, ahora simetricos.
     * <p>
     * ABAJO: nunca por debajo del 60 % del volumen de la semana 1.
     * ARRIBA: nunca por encima del 125 % de esa misma linea base.
     * <p>
     * El tope superior es nuevo. Sin el, un participante que cumple bien crece
     * un 5 % COMPUESTO sin limite: +41 % en ocho semanas, +71 % en doce. Eso es
     * un riesgo de lesion producido por la regla, no por el participante, y
     * ademas garantiza que tarde o temprano la adherencia se rompa, generando
     * una oscilacion que en los resultados parece adaptacion.
     * <p>
     * No tiene respaldo en la literatura: es una decision de diseno declarada
     * para el piloto, del mismo orden de magnitud que el piso ya existente.
     */
    private int boundedTargetVolume(AdjustmentContext context,
                                    CalculationParams.Adjustment limits,
                                    double volumeChangePct) {
        int previous = context.previousWeekVolume();
        if (previous <= 0) return 0;

        int target = (int) Math.round(previous * (1 + volumeChangePct / 100.0));

        int baseline = context.baselineWeekVolume();
        if (baseline > 0) {
            target = Math.max(target, absoluteFloor(context, limits));

            int ceiling = absoluteCeiling(context, limits);
            if (ceiling != Integer.MAX_VALUE) target = Math.min(target, ceiling);

            // Al recuperar no se pasa de la linea base: la progresion por encima
            // es otra decision, y se toma la semana siguiente.
            if (previous < baseline) target = Math.min(target, baseline);
        }

        return Math.max(1, target);
    }

    /** Piso absoluto de 18.4: no se baja del 60 % de la linea base. */
    private int absoluteFloor(AdjustmentContext context, CalculationParams.Adjustment limits) {
        int baseline = context.baselineWeekVolume();
        if (baseline <= 0) return 1;
        return (int) Math.round(baseline
                * (1 - limits.maxCumulativeVolumeReductionPct() / 100.0));
    }

    /** Techo absoluto de 18.4 sobre la linea base. MAX_VALUE si esta desactivado. */
    private int absoluteCeiling(AdjustmentContext context, CalculationParams.Adjustment limits) {
        int baseline = context.baselineWeekVolume();
        double maxIncrease = limits.maxIncreaseOrDefault();
        if (baseline <= 0 || maxIncrease == Double.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(baseline * (1 + maxIncrease / 100.0));
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