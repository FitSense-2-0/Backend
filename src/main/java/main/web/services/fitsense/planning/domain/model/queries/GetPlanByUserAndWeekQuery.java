package main.web.services.fitsense.planning.domain.model.queries;

import java.time.LocalDate;

public record GetPlanByUserAndWeekQuery(Long userId, LocalDate weekStartDate) {}
