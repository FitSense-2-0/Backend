package main.web.services.fitsense.planning.domain.model.valueobjects;

import java.util.List;

/**
 * El bloque "adjustment" de 19.1: lo que la regla ORDENA para esta semana.
 * <p>
 * target_volume, min y max son el corazon del mecanismo. La regla decide el
 * CUANTO y lo fija como un rango; el generador decide el COMO. La validacion 8
 * comprueba que el volumen entregado cae dentro, y esa comprobacion es lo que
 * convierte "el sistema se adapta" en algo demostrable.
 */
public record PlanAdjustment(
        List<AdjustmentType> types,
        Integer targetVolume,
        Integer targetVolumeMin,
        Integer targetVolumeMax,
        double targetVolumeChangePct,
        double loadChangePct,
        Integer maxDifficultyLevel,
        Integer forcedDaysPerWeek,
        Integer forcedSessionMinutes,
        String reason,
        String dominantSkipReason,
        String distributionHint
) {

    /** Primera semana: sin ajuste previo. No hay rango de volumen que verificar. */
    public static PlanAdjustment none() {
        return new PlanAdjustment(List.of(AdjustmentType.NONE), null, null, null,
                0.0, 0.0, null, null, null, null, null, null);
    }

    public boolean isActive() {
        return targetVolume != null;
    }

    public boolean has(AdjustmentType type) {
        return types != null && types.contains(type);
    }

    /** Cuando la orden incluye LOWER_LOAD, target_load_kg se deja en NULL (20.5). */
    public boolean clearsLoad() {
        return has(AdjustmentType.LOWER_LOAD);
    }
}
