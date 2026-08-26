package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.commands.*;

import java.util.Optional;

public interface WeeklyTrainingPlanCommandService {
    Optional<WeeklyTrainingPlan> handle(GenerateWeeklyPlanCommand command);
    void handle(StartPlannedWorkoutCommand command);
    void handle(RecordWorkoutOutcomeCommand command);
    void handle(SkipPlannedWorkoutCommand command);
    void handle(ClosePlanWeekCommand command);
    int handle(ExpireOverdueWorkoutsCommand command);
}
