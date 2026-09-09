package main.web.services.fitsense.adaptation.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * Insumos de la decision. Todo llega por parametro para que la tabla sea
 * reproducible: dados los mismos numeros, la misma orden, siempre.
 */
public record AdjustmentContext(
        BigDecimal weightedAdherencePct,
        String dominantSkipReason,
        int previousWeekVolume,
        int baselineWeekVolume,
        int previousDaysPerWeek,
        int previousSessionMinutes,
        int previousMaxDifficulty,

        /**
         * Adherencia de la semana ANTERIOR a la medida. Permite exigir dos
         * semanas seguidas de cumplimiento antes de progresar, en lugar de
         * subir el volumen por calendario.
         * <p>
         * NULL cuando no existe esa semana (la primera del participante) o
         * cuando no fue medible. En ambos casos no se progresa: sin evidencia
         * de desempeno sostenido, la opcion conservadora.
         */
        BigDecimal previousWeekAdherencePct,

        /**
         * RPE promedio de la semana medida. Hoy solo se usa para bloquear la
         * progresion cuando falta; el techo por RPE alto esta desactivado
         * mientras la escala no este instrumentada.
         */
        BigDecimal averageSessionRpe
) {}