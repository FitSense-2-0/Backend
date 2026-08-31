package main.web.services.fitsense.orchestration.application.internal;

import main.web.services.fitsense.adaptation.interfaces.acl.AdaptationContextFacade;
import main.web.services.fitsense.adaptation.interfaces.acl.AdjustmentOrderView;
import main.web.services.fitsense.analytics.interfaces.acl.AnalyticsContextFacade;
import main.web.services.fitsense.planning.domain.model.valueobjects.AdjustmentType;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanAdjustment;
import main.web.services.fitsense.planning.interfaces.acl.PlanningContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * La tarea semanal de la seccion 22, para un participante:
 * <pre>
 *   1. Cerrar la semana que termino.
 *   2. Calcular sus metricas.
 *   3. Evaluar la intervencion de la semana anterior (adherence_after_pct).
 *   4. Decidir el ajuste de la semana nueva.
 *   5. Generar el plan con ese ajuste y enlazarlo a la intervencion.
 * </pre>
 * Esto NO es un bounded context: no tiene tablas ni lenguaje propio. Es un
 * process manager, y vive aparte porque la alternativa seria que uno de los
 * cuatro contextos coordinara a los otros tres, convirtiendolo en el centro de
 * un grafo de dependencias que el diseno separo a proposito.
 * <p>
 * Solo habla con facades. No conoce ningun agregado ni repositorio.
 */
@Service
public class WeeklyCycleService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyCycleService.class);

    private final PlanningContextFacade planningContextFacade;
    private final AnalyticsContextFacade analyticsContextFacade;
    private final AdaptationContextFacade adaptationContextFacade;

    public WeeklyCycleService(PlanningContextFacade planningContextFacade,
                              AnalyticsContextFacade analyticsContextFacade,
                              AdaptationContextFacade adaptationContextFacade) {
        this.planningContextFacade = planningContextFacade;
        this.analyticsContextFacade = analyticsContextFacade;
        this.adaptationContextFacade = adaptationContextFacade;
    }

    /**
     * Ejecuta el ciclo semanal de un participante.
     * <p>
     * noRollbackFor: la generacion del plan puede fallar legitimamente (19.4) y
     * ese fallo NO debe deshacer el cierre de semana ni las metricas, que ya se
     * calcularon bien. Sin esto, capturar la excepcion no basta: Spring marca la
     * transaccion como rollback-only y revienta al hacer commit con
     * UnexpectedRollbackException, perdiendo tambien lo que si funciono.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public Optional<Long> runFor(Long userId, LocalDate measuredWeekStart, LocalDate newWeekStart) {
        // 1. Cerrar. El plan deja de admitir sesiones nuevas.
        planningContextFacade.closeWeek(userId, measuredWeekStart);

        // 2. Medir. Idempotente: recalcular sobrescribe la misma fila.
        var metrics = analyticsContextFacade.calculateWeek(userId, measuredWeekStart);

        // 3. Cerrar el circulo de la intervencion ANTERIOR antes de decidir la
        //    nueva: la adherencia recien calculada es justamente el resultado
        //    del ajuste que se aplico la semana pasada.
        metrics.ifPresent(view ->
                adaptationContextFacade.recordOutcome(userId, view.weightedAdherencePct()));

        // 4. Decidir. Vacio en la primera semana o si no hubo plan.
        var order = adaptationContextFacade.decideForWeek(userId, measuredWeekStart);

        // 5. Generar.
        var adjustment = order.map(WeeklyCycleService::toAdjustment).orElse(PlanAdjustment.none());

        Optional<Long> planId;
        try {
            planId = planningContextFacade.generateWeeklyPlan(userId, newWeekStart, adjustment);
        } catch (RuntimeException e) {
            // 19.4: si no hay plan valido la semana queda sin plan y su
            // adherencia sera NULL. Es un dato del estudio, pero no puede tumbar
            // el cierre del resto de la cohorte.
            log.error("No se pudo generar el plan del usuario {} para la semana del {}: {}",
                    userId, newWeekStart, e.getMessage());
            return Optional.empty();
        }

        order.ifPresent(view -> planId.ifPresent(
                id -> adaptationContextFacade.linkResultingPlan(view.interventionId(), id)));

        return planId;
    }

    private static PlanAdjustment toAdjustment(AdjustmentOrderView order) {
        List<AdjustmentType> types = order.adjustmentTypes().stream()
                .map(AdjustmentType::valueOf)
                .toList();

        return new PlanAdjustment(
                types,
                order.targetVolume(),
                order.targetVolumeMin(),
                order.targetVolumeMax(),
                order.targetVolumeChangePct(),
                order.loadChangePct(),
                order.forcedMaxDifficulty(),
                order.forcedDaysPerWeek(),
                order.forcedSessionMinutes(),
                order.reason(),
                order.dominantSkipReason(),
                order.distributionHint());
    }
}