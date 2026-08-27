package main.web.services.fitsense.adaptation.interfaces.acl;

import java.util.List;

/**
 * La orden que la adaptacion emite para la semana siguiente, junto al id de la
 * intervencion que la respalda. La tarea semanal la traduce a la peticion de
 * generacion.
 */
public record AdjustmentOrderView(
        Long interventionId,
        List<String> adjustmentTypes,
        String primaryType,
        int targetVolume,
        int targetVolumeMin,
        int targetVolumeMax,
        double targetVolumeChangePct,
        double loadChangePct,
        Integer forcedDaysPerWeek,
        Integer forcedSessionMinutes,
        Integer forcedMaxDifficulty,
        String reason,
        String dominantSkipReason,
        String distributionHint,
        String message
) {}
