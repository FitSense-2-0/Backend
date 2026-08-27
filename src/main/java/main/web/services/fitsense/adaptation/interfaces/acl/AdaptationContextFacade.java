package main.web.services.fitsense.adaptation.interfaces.acl;

import main.web.services.fitsense.adaptation.domain.model.commands.DecideWeeklyAdjustmentCommand;
import main.web.services.fitsense.adaptation.domain.model.commands.LinkResultingPlanCommand;
import main.web.services.fitsense.adaptation.domain.model.commands.RecordInterventionOutcomeCommand;
import main.web.services.fitsense.adaptation.domain.services.UserInterventionCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Unico punto de entrada a adaptation. Lo consume la tarea semanal: decide el
 * ajuste con las metricas ya cerradas y, una vez generado el plan, registra que
 * salio de la orden.
 */
@Service
public class AdaptationContextFacade {

    private final UserInterventionCommandService commandService;

    public AdaptationContextFacade(UserInterventionCommandService commandService) {
        this.commandService = commandService;
    }

    /** Vacio si no habia nada que ajustar: primera semana o semana sin plan. */
    @Transactional
    public Optional<AdjustmentOrderView> decideForWeek(Long userId, LocalDate measuredWeekStartDate) {
        return commandService.handle(new DecideWeeklyAdjustmentCommand(userId, measuredWeekStartDate))
                .map(AdaptationContextFacade::toView);
    }

    @Transactional
    public void linkResultingPlan(Long interventionId, Long resultingPlanId) {
        commandService.handle(new LinkResultingPlanCommand(interventionId, resultingPlanId));
    }

    @Transactional
    public void recordOutcome(Long userId, BigDecimal adherenceAfterPct) {
        commandService.handle(new RecordInterventionOutcomeCommand(userId, adherenceAfterPct));
    }

    private static AdjustmentOrderView toView(UserInterventionCommandService.AdjustmentResult result) {
        var intervention = result.intervention();
        var decision = result.decision();

        return new AdjustmentOrderView(
                intervention.getId(),
                decision.typeNames(),
                decision.primaryType(),
                decision.targetVolume(),
                decision.targetVolumeMin(),
                decision.targetVolumeMax(),
                decision.targetVolumeChangePct(),
                decision.loadChangePct(),
                decision.forcedDaysPerWeek(),
                decision.forcedSessionMinutes(),
                decision.forcedMaxDifficulty(),
                decision.reason(),
                intervention.getTriggerSkipReason() == null ? null
                        : intervention.getTriggerSkipReason().name(),
                decision.distributionHint(),
                decision.message());
    }
}
