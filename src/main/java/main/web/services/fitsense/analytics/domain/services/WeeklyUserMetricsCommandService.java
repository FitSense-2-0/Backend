package main.web.services.fitsense.analytics.domain.services;

import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import main.web.services.fitsense.analytics.domain.model.commands.CalculateWeeklyMetricsCommand;

import java.util.Optional;

public interface WeeklyUserMetricsCommandService {
    Optional<WeeklyUserMetrics> handle(CalculateWeeklyMetricsCommand command);
}
