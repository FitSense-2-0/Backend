package main.web.services.fitsense.execution.domain.services;

import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.queries.GetCurrentSessionQuery;
import main.web.services.fitsense.execution.domain.model.queries.GetSessionByIdQuery;
import main.web.services.fitsense.execution.domain.model.queries.GetSessionsByWeekQuery;

import java.util.List;
import java.util.Optional;

public interface WorkoutSessionQueryService {
    Optional<WorkoutSession> handle(GetSessionByIdQuery query);
    Optional<WorkoutSession> handle(GetCurrentSessionQuery query);
    List<WorkoutSession> handle(GetSessionsByWeekQuery query);
}
