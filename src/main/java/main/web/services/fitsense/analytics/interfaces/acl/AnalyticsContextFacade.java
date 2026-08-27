package main.web.services.fitsense.analytics.interfaces.acl;

import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import main.web.services.fitsense.analytics.domain.model.commands.CalculateWeeklyMetricsCommand;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsByWeekQuery;
import main.web.services.fitsense.analytics.domain.services.WeeklyUserMetricsCommandService;
import main.web.services.fitsense.analytics.domain.services.WeeklyUserMetricsQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Unico punto de entrada a analytics. Lo consumen adaptation (para decidir el
 * ajuste) y la tarea semanal (para disparar el cierre).
 */
@Service
public class AnalyticsContextFacade {

    private final WeeklyUserMetricsCommandService commandService;
    private final WeeklyUserMetricsQueryService queryService;

    public AnalyticsContextFacade(WeeklyUserMetricsCommandService commandService,
                                  WeeklyUserMetricsQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /** Cierra la semana. Idempotente: recalcular sobrescribe. */
    @Transactional
    public Optional<WeeklyMetricsView> calculateWeek(Long userId, LocalDate weekStartDate) {
        return commandService.handle(new CalculateWeeklyMetricsCommand(userId, weekStartDate))
                .map(AnalyticsContextFacade::toView);
    }

    @Transactional(readOnly = true)
    public Optional<WeeklyMetricsView> fetchWeek(Long userId, LocalDate weekStartDate) {
        return queryService.handle(new GetWeeklyMetricsByWeekQuery(userId, weekStartDate))
                .map(AnalyticsContextFacade::toView);
    }

    private static WeeklyMetricsView toView(WeeklyUserMetrics metrics) {
        return new WeeklyMetricsView(
                metrics.getId(),
                metrics.getPlanId(),
                metrics.getWeekNumber(),
                metrics.getWeekStartDate(),
                metrics.isHasActivePlan(),
                metrics.getWeightedAdherencePct(),
                metrics.getAverageSessionRpe(),
                metrics.getConsecutiveSkips(),
                metrics.getDominantSkipReason() == null ? null : metrics.getDominantSkipReason().name(),
                metrics.getRiskLevel() == null ? null : metrics.getRiskLevel().name(),
                metrics.isDropout(),
                metrics.getScheduledWorkouts(),
                metrics.getAssignedExercises());
    }
}
