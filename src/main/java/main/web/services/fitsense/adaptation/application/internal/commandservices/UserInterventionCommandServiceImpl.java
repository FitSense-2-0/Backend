package main.web.services.fitsense.adaptation.application.internal.commandservices;

import main.web.services.fitsense.analytics.interfaces.acl.WeeklyMetricsView;
import main.web.services.fitsense.adaptation.application.internal.outboundservices.acl.*;
import main.web.services.fitsense.adaptation.domain.exceptions.InterventionNotFoundException;
import main.web.services.fitsense.adaptation.domain.model.aggregates.UserIntervention;
import main.web.services.fitsense.adaptation.domain.model.commands.DecideWeeklyAdjustmentCommand;
import main.web.services.fitsense.adaptation.domain.model.commands.LinkResultingPlanCommand;
import main.web.services.fitsense.adaptation.domain.model.commands.RecordInterventionOutcomeCommand;
import main.web.services.fitsense.adaptation.domain.model.valueobjects.AdjustmentContext;
import main.web.services.fitsense.adaptation.domain.services.AdjustmentDecisionTable;
import main.web.services.fitsense.adaptation.domain.services.UserInterventionCommandService;
import main.web.services.fitsense.adaptation.infrastructure.persistence.jpa.repositories.UserInterventionRepository;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserInterventionCommandServiceImpl implements UserInterventionCommandService {

    private static final Logger log = LoggerFactory.getLogger(UserInterventionCommandServiceImpl.class);

    private final UserInterventionRepository interventionRepository;
    private final AdjustmentDecisionTable decisionTable;
    private final ExternalAnalyticsService externalAnalyticsService;
    private final ExternalProfilingService externalProfilingService;
    private final ExternalPlanningService externalPlanningService;
    private final ExternalConfigurationService externalConfigurationService;

    public UserInterventionCommandServiceImpl(UserInterventionRepository interventionRepository,
                                              AdjustmentDecisionTable decisionTable,
                                              ExternalAnalyticsService externalAnalyticsService,
                                              ExternalProfilingService externalProfilingService,
                                              ExternalPlanningService externalPlanningService,
                                              ExternalConfigurationService externalConfigurationService) {
        this.interventionRepository = interventionRepository;
        this.decisionTable = decisionTable;
        this.externalAnalyticsService = externalAnalyticsService;
        this.externalProfilingService = externalProfilingService;
        this.externalPlanningService = externalPlanningService;
        this.externalConfigurationService = externalConfigurationService;
    }

    /**
     * 18.5: se crea una fila SIEMPRE, aunque el ajuste sea NONE, para que el
     * conjunto de datos tenga una observacion por participante y por semana sin
     * huecos. Lo unico que impide crearla es que no haya semana medible.
     */
    @Override
    @Transactional
    public Optional<AdjustmentResult> handle(DecideWeeklyAdjustmentCommand command) {
        var week = TrainingWeek.containing(command.measuredWeekStartDate());

        var metrics = externalAnalyticsService.fetchWeek(command.userId(), week.startDate())
                .orElse(null);

        // Sin metricas o sin plan medido no hay adherencia que interpretar: es la
        // primera semana del participante, o una semana en la que el sistema no
        // llego a proponer nada. No es un error.
        if (metrics == null || !metrics.isActionable()) {
            log.debug("Sin metricas accionables para el usuario {} en la semana del {}",
                    command.userId(), week.startDate());
            return Optional.empty();
        }

        var configuration = externalConfigurationService.fetchActive();
        var limits = configuration.params().adjustment();
        int divisor = limits.durationToRepsDivisor();

        var profile = externalProfilingService.fetchProfile(command.userId());
        // Base de REDUCE_DAYS: las sesiones que de verdad se planificaron, con el
        // tope por nivel de la division semanal (principiante 4, resto 6; ver
        // PlanGenerationContext.effectiveDaysPerWeek). Sin el tope, una
        // principiante con 6 dias recibiria "reducir a 5" y seguiria en 4: la
        // orden no tendria efecto.
        int previousDays = profile.map(p -> Math.min(p.daysPerWeek(),
                        "BEGINNER".equals(p.fitnessLevel()) ? 4 : 6))
                .orElse(limits.minDaysPerWeek());
        int previousMinutes = profile.map(p -> (int) p.sessionMinutes())
                .orElse(limits.minSessionMinutes());
        int previousDifficulty = profile.map(p -> p.maxDifficultyLevel()).orElse(2);

        int previousVolume = externalPlanningService.weekVolume(
                command.userId(), week.startDate(), divisor);
        int baselineVolume = externalPlanningService.baselineVolume(command.userId(), divisor);

        // La semana anterior a la medida. Con ella se exige desempeno sostenido
        // antes de progresar, en lugar de subir el volumen por calendario.
        // Ausente en la primera semana del participante: entonces no se progresa.
        var previousAdherence = externalAnalyticsService
                .fetchWeek(command.userId(), week.previous().startDate())
                .filter(WeeklyMetricsView::isActionable)
                .map(WeeklyMetricsView::weightedAdherencePct)
                .orElse(null);

        var context = new AdjustmentContext(
                metrics.weightedAdherencePct(),
                metrics.dominantSkipReason(),
                previousVolume,
                baselineVolume,
                previousDays, previousMinutes, previousDifficulty,
                previousAdherence,
                metrics.averageSessionRpe());

        var decision = decisionTable.decide(context, configuration.params());

        // uq_ui_metric: una sola fila por semana medida. Si ya existe se devuelve
        // con la decision recalculada, que es identica por ser determinista.
        var existing = interventionRepository.findByWeeklyMetricId(metrics.weeklyMetricId());
        if (existing.isPresent())
            return Optional.of(new AdjustmentResult(existing.get(), decision));

        var intervention = new UserIntervention(
                command.userId(),
                metrics.weeklyMetricId(),
                metrics.planId(),
                metrics.weightedAdherencePct(),
                metrics.dominantSkipReason() == null ? null
                        : SkipReason.valueOf(metrics.dominantSkipReason()),
                decision,
                previousVolume,
                previousDays, previousMinutes, previousDifficulty,
                configuration.version());

        return Optional.of(new AdjustmentResult(interventionRepository.save(intervention), decision));
    }

    /**
     * Registra el volumen que el generador entrego realmente.
     * <p>
     * Comparar target_volume_change_pct con actual_volume_change_pct es lo que
     * responde si el generador obedecio la orden, que es una pregunta distinta
     * de si el ajuste funciono.
     */
    @Override
    @Transactional
    public Optional<UserIntervention> handle(LinkResultingPlanCommand command) {
        var intervention = interventionRepository.findById(command.interventionId())
                .orElseThrow(() -> new InterventionNotFoundException(command.interventionId()));

        var configuration = externalConfigurationService.fetchActive();
        int divisor = configuration.params().adjustment().durationToRepsDivisor();

        // El volumen se lee de la semana del plan que se esta enlazando, no de la
        // semana que contiene la fecha de hoy. No son la misma cuando el ciclo
        // corre con retraso o se relanza despues de un fallo de generacion, y en
        // ese caso resulting_week_volume y actual_volume_change_pct quedaban con
        // el numero de otra semana.
        int resultingVolume = externalPlanningService.weekVolume(
                intervention.getUserId(), command.resultingWeekStart(), divisor);

        intervention.linkResultingPlan(command.resultingPlanId(), resultingVolume);
        return Optional.of(interventionRepository.save(intervention));
    }

    /**
     * Paso 3 de la tarea semanal: evalua la intervencion que PRODUJO el plan
     * medido.
     * <p>
     * Antes se buscaba "la ultima intervencion del participante" por fecha de
     * aplicacion. Mientras no falla nada coinciden, pero basta una semana sin
     * plan para que no se cree intervencion, y entonces la ultima sigue siendo
     * la de dos semanas atras: la adherencia recien medida se escribia encima de
     * una orden que no la produjo, sobrescribiendo su resultado anterior.
     * <p>
     * Eso corrompia adherence_after_pct, que es justamente la medida de si la
     * adaptacion funciono. La correccion del rollback (problema 13) aumenta la
     * exposicion a este fallo, porque ahora el ciclo sobrevive al fallo de
     * generacion y sigue adelante dejando la semana sin intervencion.
     * <p>
     * Buscar por resulting_plan_id responde la pregunta correcta: la adherencia
     * de un plan puntua a la orden que genero ese plan. Si esa orden no existe,
     * no se puntua nada, y un nulo honesto vale mas que un numero ajeno.
     */
    @Override
    @Transactional
    public void handle(RecordInterventionOutcomeCommand command) {
        if (command.measuredPlanId() == null) return;

        interventionRepository.findByResultingPlanId(command.measuredPlanId())
                .ifPresent(intervention -> {
                    var tolerance = externalConfigurationService.fetchActive()
                            .params().adjustment().volumeTolerancePct();
                    intervention.recordOutcome(command.adherenceAfterPct(), tolerance);
                    interventionRepository.save(intervention);
                });
    }
}