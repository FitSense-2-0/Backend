package main.web.services.fitsense.execution.domain.services;

import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.commands.*;

import java.util.Optional;

public interface WorkoutSessionCommandService {
    Optional<WorkoutSession> handle(StartWorkoutSessionCommand command);
    Optional<WorkoutSession> handle(RecordSessionExerciseCommand command);
    Optional<WorkoutSession> handle(FinishWorkoutSessionCommand command);
    Optional<WorkoutSession> handle(AbandonWorkoutSessionCommand command);
    Optional<WorkoutSession> handle(ReportWorkoutCommand command);
}
