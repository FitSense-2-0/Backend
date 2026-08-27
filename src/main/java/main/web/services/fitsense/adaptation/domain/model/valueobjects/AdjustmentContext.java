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
        int previousMaxDifficulty
) {}
