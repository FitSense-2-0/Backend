package main.web.services.fitsense.planning.domain.model.queries;

import java.time.LocalDate;

public record GetActivePlanQuery(Long userId, LocalDate onDate) {}
