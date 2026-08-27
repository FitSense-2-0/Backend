package main.web.services.fitsense.adaptation.application.internal.outboundservices.acl;

import main.web.services.fitsense.planning.interfaces.acl.PlanningContextFacade;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Capa anticorrupcion hacia planning: los dos volumenes contra los que se mide
 * el ajuste. El de la semana anterior fija el punto de partida; el de la semana
 * 1 fija el suelo del tope acumulado de 18.4.
 */
@Service
public class ExternalPlanningService {

    private final PlanningContextFacade planningContextFacade;

    public ExternalPlanningService(PlanningContextFacade planningContextFacade) {
        this.planningContextFacade = planningContextFacade;
    }

    public int weekVolume(Long userId, LocalDate weekStartDate, int divisor) {
        return planningContextFacade.fetchWeekVolume(userId, weekStartDate, divisor);
    }

    public int baselineVolume(Long userId, int divisor) {
        return planningContextFacade.fetchBaselineVolume(userId, divisor);
    }
}
