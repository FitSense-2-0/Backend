package main.web.services.fitsense.analytics.application.internal.commandservices;

import main.web.services.fitsense.analytics.application.internal.outboundservices.acl.ExternalConfigurationService;
import main.web.services.fitsense.analytics.application.internal.outboundservices.acl.ExternalExecutionService;
import main.web.services.fitsense.analytics.application.internal.outboundservices.acl.ExternalPlanningService;
import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import main.web.services.fitsense.analytics.domain.model.commands.CalculateWeeklyMetricsCommand;
import main.web.services.fitsense.analytics.domain.services.WeeklyMetricsCalculator;
import main.web.services.fitsense.analytics.domain.services.WeeklyUserMetricsCommandService;
import main.web.services.fitsense.analytics.infrastructure.persistence.jpa.repositories.WeeklyUserMetricsRepository;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import main.web.services.fitsense.shared.infrastructure.json.JsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class WeeklyUserMetricsCommandServiceImpl implements WeeklyUserMetricsCommandService {

    private final WeeklyUserMetricsRepository metricsRepository;
    private final WeeklyMetricsCalculator calculator;
    private final ExternalPlanningService externalPlanningService;
    private final ExternalExecutionService externalExecutionService;
    private final ExternalConfigurationService externalConfigurationService;
    private final JsonSupport jsonSupport;

    public WeeklyUserMetricsCommandServiceImpl(WeeklyUserMetricsRepository metricsRepository,
                                               WeeklyMetricsCalculator calculator,
                                               ExternalPlanningService externalPlanningService,
                                               ExternalExecutionService externalExecutionService,
                                               ExternalConfigurationService externalConfigurationService,
                                               JsonSupport jsonSupport) {
        this.metricsRepository = metricsRepository;
        this.calculator = calculator;
        this.externalPlanningService = externalPlanningService;
        this.externalExecutionService = externalExecutionService;
        this.externalConfigurationService = externalConfigurationService;
        this.jsonSupport = jsonSupport;
    }

    @Override
    @Transactional
    public Optional<WeeklyUserMetrics> handle(CalculateWeeklyMetricsCommand command) {
        var week = TrainingWeek.containing(command.weekStartDate());
        var configuration = externalConfigurationService.fetchActive();

        var plan = externalPlanningService.fetchWeekPlan(command.userId(), week.startDate());
        var sessions = externalExecutionService.fetchWeekSessions(
                command.userId(), week.startDate(), week.endDate());
        var daysSinceLastWorkout = externalExecutionService.daysSinceLastWorkout(
                command.userId(), week.endDate());

        // La caida se mide contra la semana anterior YA CALCULADA, no contra la
        // que se este calculando ahora: comparar con una fila a medio escribir
        // haria que el riesgo dependa del orden en que corre la tarea.
        var previousAdherence = metricsRepository
                .findByUserIdAndWeekStartDate(command.userId(), week.previous().startDate())
                .map(WeeklyUserMetrics::getWeightedAdherencePct)
                .orElse(null);

        var calculation = calculator.calculate(plan, sessions, previousAdherence,
                daysSinceLastWorkout, configuration.params());

        var riskFactorsJson = calculation.riskFactors() == null || calculation.riskFactors().isEmpty()
                ? null
                : jsonSupport.write(calculation.riskFactors());

        var existing = metricsRepository.findByUserIdAndWeekStartDate(
                command.userId(), week.startDate());

        var metrics = existing
                .map(found -> {
                    found.recalculate(calculation, riskFactorsJson, configuration.version());
                    return found;
                })
                .orElseGet(() -> new WeeklyUserMetrics(command.userId(), week.startDate(),
                        week.endDate(), calculation, riskFactorsJson, configuration.version()));

        return Optional.of(metricsRepository.save(metrics));
    }
}
