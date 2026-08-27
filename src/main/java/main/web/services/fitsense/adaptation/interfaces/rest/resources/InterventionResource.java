package main.web.services.fitsense.adaptation.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record InterventionResource(
        Long interventionId,
        Long sourcePlanId,
        Long resultingPlanId,
        BigDecimal triggerAdherencePct,
        String triggerSkipReason,
        List<String> adjustmentTypes,
        BigDecimal targetVolumeChangePct,
        Integer previousWeekVolume,
        Integer resultingWeekVolume,
        BigDecimal actualVolumeChangePct,
        Short daysChange,
        Short difficultyChange,
        BigDecimal durationChangePct,
        BigDecimal loadChangePct,
        String messageShown,
        String ruleVersion,
        OffsetDateTime appliedAt,
        BigDecimal adherenceAfterPct,
        String outcome
) {}
