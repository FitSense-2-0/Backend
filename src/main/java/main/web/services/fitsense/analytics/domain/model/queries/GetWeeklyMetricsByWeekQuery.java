package main.web.services.fitsense.analytics.domain.model.queries;

import java.time.LocalDate;

public record GetWeeklyMetricsByWeekQuery(Long userId, LocalDate weekStartDate) {}
