package main.web.services.fitsense.execution.interfaces.rest.transform;

import main.web.services.fitsense.execution.domain.model.commands.ReportWorkoutCommand;
import main.web.services.fitsense.execution.interfaces.rest.resources.ReportWorkoutResource;

public class ReportWorkoutCommandFromResourceAssembler {

    private ReportWorkoutCommandFromResourceAssembler() {}

    public static ReportWorkoutCommand toCommandFromResource(Long userId, Long plannedWorkoutId,
                                                             ReportWorkoutResource resource) {
        var exercises = resource.exercises().stream()
                .map(exercise -> new ReportWorkoutCommand.ReportedExercise(
                        exercise.plannedExerciseId(),
                        exercise.actualSets(),
                        exercise.actualRepsTotal(),
                        exercise.actualDurationSeconds(),
                        exercise.actualLoadKg(),
                        exercise.skipReason()))
                .toList();

        return new ReportWorkoutCommand(userId, plannedWorkoutId, resource.performedAt(),
                resource.sessionRpe(), resource.satisfaction(), resource.activeMinutes(), exercises);
    }
}
