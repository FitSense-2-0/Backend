package main.web.services.fitsense.adaptation.interfaces.rest.transform;

import main.web.services.fitsense.adaptation.domain.model.aggregates.UserIntervention;
import main.web.services.fitsense.adaptation.interfaces.rest.resources.InterventionResource;

public class InterventionResourceFromEntityAssembler {

    private InterventionResourceFromEntityAssembler() {}

    public static InterventionResource toResourceFromEntity(UserIntervention entity) {
        return new InterventionResource(
                entity.getId(),
                entity.getSourcePlanId(),
                entity.getResultingPlanId(),
                entity.getTriggerAdherencePct(),
                entity.getTriggerSkipReason() == null ? null : entity.getTriggerSkipReason().name(),
                entity.adjustmentTypesAsList(),
                entity.getTargetVolumeChangePct(),
                entity.getPreviousWeekVolume(),
                entity.getResultingWeekVolume(),
                entity.getActualVolumeChangePct(),
                entity.getDaysChange(),
                entity.getDifficultyChange(),
                entity.getDurationChangePct(),
                entity.getLoadChangePct(),
                entity.getMessageShown(),
                entity.getRuleVersion(),
                entity.getAppliedAt(),
                entity.getAdherenceAfterPct(),
                entity.getOutcome());
    }
}
