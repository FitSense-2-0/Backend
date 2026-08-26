package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface WeeklyTrainingPlanQueryService {
    Optional<WeeklyTrainingPlan> handle(GetActivePlanQuery query);
    Optional<WeeklyTrainingPlan> handle(GetPlanByIdQuery query);
    Optional<WeeklyTrainingPlan> handle(GetPlanByUserAndWeekQuery query);
    List<WeeklyTrainingPlan> handle(GetPlanHistoryQuery query);
    Optional<PlannedWorkout> handle(GetPlannedWorkoutByIdQuery query);
}
