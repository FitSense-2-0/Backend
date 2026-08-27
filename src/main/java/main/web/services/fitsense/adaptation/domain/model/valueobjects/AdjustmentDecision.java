package main.web.services.fitsense.adaptation.domain.model.valueobjects;

import java.util.List;

/**
 * Lo que la regla decidio. Es el contrato entre la tabla de decision y todo lo
 * demas: se guarda en user_interventions y se traduce a la orden que recibe el
 * generador.
 * <p>
 * targetVolume, min y max son lo que verifica la validacion 8. Fijar un rango y
 * no un numero exacto es lo que permite que la IA reparta como quiera mientras
 * respete el CUANTO.
 */
public record AdjustmentDecision(
        List<AdjustmentType> types,
        int targetVolume,
        int targetVolumeMin,
        int targetVolumeMax,
        double targetVolumeChangePct,
        double loadChangePct,
        Integer forcedDaysPerWeek,
        Integer forcedSessionMinutes,
        Integer forcedMaxDifficulty,
        String reason,
        String distributionHint,
        String message
) {
    public List<String> typeNames() {
        return types.stream().map(Enum::name).toList();
    }

    public String primaryType() {
        return types.isEmpty() ? AdjustmentType.NONE.name() : types.get(0).name();
    }

    public boolean isProgression() {
        return targetVolumeChangePct > 0;
    }
}
