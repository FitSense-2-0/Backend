package main.web.services.fitsense.execution.domain.model.queries;

import java.time.LocalDate;

public record GetSessionsByWeekQuery(Long userId, LocalDate weekStartDate, LocalDate weekEndDate) {}
