package main.web.services.fitsense.analytics.domain.services;

import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsByWeekQuery;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsHistoryQuery;

import java.util.List;
import java.util.Optional;

public interface WeeklyUserMetricsQueryService {
    List<WeeklyUserMetrics> handle(GetWeeklyMetricsHistoryQuery query);
    Optional<WeeklyUserMetrics> handle(GetWeeklyMetricsByWeekQuery query);
}
