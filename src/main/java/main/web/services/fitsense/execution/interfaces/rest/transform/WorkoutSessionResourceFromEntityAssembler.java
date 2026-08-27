package main.web.services.fitsense.execution.interfaces.rest.transform;

import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.interfaces.rest.resources.SessionExerciseResource;
import main.web.services.fitsense.execution.interfaces.rest.resources.WorkoutSessionResource;

public class WorkoutSessionResourceFromEntityAssembler {

    private WorkoutSessionResourceFromEntityAssembler() {}

    public static WorkoutSessionResource toResourceFromEntity(WorkoutSession entity) {
        var exercises = entity.exercisesView().stream()
                .map(exercise -> new SessionExerciseResource(
                        exercise.getId(),
                        exercise.getPlannedExerciseId(),
                        exercise.getActualSets(),
                        exercise.getActualRepsTotal(),
                        exercise.getActualDurationSeconds(),
                        exercise.getActualLoadKg(),
                        exercise.getCompletionPercentage(),
                        exercise.getStatus().name(),
                        exercise.getSkipReason() == null ? null : exercise.getSkipReason().name()))
                .toList();

        return new WorkoutSessionResource(
                entity.getId(),
                entity.getPlannedWorkoutId(),
                entity.getPlanId(),
                entity.getAttemptNumber(),
                entity.isCountsTowardAdherence(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getActiveMinutes(),
                entity.getCompletionPercentage(),
                entity.getSessionRpe(),
                entity.getSatisfaction(),
                entity.getSource().name(),
                entity.getStatus().name(),
                exercises);
    }
}
