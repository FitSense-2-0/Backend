package main.web.services.fitsense.analytics.application.internal.queryservices;

import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsByWeekQuery;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsHistoryQuery;
import main.web.services.fitsense.analytics.domain.services.WeeklyUserMetricsQueryService;
import main.web.services.fitsense.analytics.infrastructure.persistence.jpa.repositories.WeeklyUserMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WeeklyUserMetricsQueryServiceImpl implements WeeklyUserMetricsQueryService {

    private final WeeklyUserMetricsRepository metricsRepository;

    public WeeklyUserMetricsQueryServiceImpl(WeeklyUserMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyUserMetrics> handle(GetWeeklyMetricsHistoryQuery query) {
        return metricsRepository.findByUserIdOrderByWeekStartDateDesc(query.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklyUserMetrics> handle(GetWeeklyMetricsByWeekQuery query) {
        return metricsRepository.findByUserIdAndWeekStartDate(query.userId(), query.weekStartDate());
    }
}
