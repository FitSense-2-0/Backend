package main.web.services.fitsense.adaptation.domain.services;

import main.web.services.fitsense.adaptation.domain.model.aggregates.UserIntervention;
import main.web.services.fitsense.adaptation.domain.model.commands.DecideWeeklyAdjustmentCommand;
import main.web.services.fitsense.adaptation.domain.model.commands.LinkResultingPlanCommand;
import main.web.services.fitsense.adaptation.domain.model.commands.RecordInterventionOutcomeCommand;
import main.web.services.fitsense.adaptation.domain.model.valueobjects.AdjustmentDecision;

import java.util.Optional;

public interface UserInterventionCommandService {

    /**
     * La decision viaja junto a la fila guardada porque la orden completa (dias,
     * minutos y dificultad forzados) es mas rica que lo que la tabla conserva:
     * la tabla guarda deltas para el analisis, el generador necesita valores
     * absolutos y el rango de volumen.
     */
    record AdjustmentResult(UserIntervention intervention, AdjustmentDecision decision) {}

    Optional<AdjustmentResult> handle(DecideWeeklyAdjustmentCommand command);
    Optional<UserIntervention> handle(LinkResultingPlanCommand command);
    void handle(RecordInterventionOutcomeCommand command);
}
