package main.web.services.fitsense.planning.interfaces.rest.transform;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlanSummaryResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlannedExerciseResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlannedWorkoutResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.WeeklyTrainingPlanResource;

import java.util.Map;

/**
 * Los nombres de los ejercicios llegan por parametro y no se leen del agregado:
 * planning guarda ids, no nombres. Resolverlos aqui mantiene el catalogo como
 * unica fuente del nombre y evita duplicar texto que puede quedar desfasado.
 */
public class WeeklyTrainingPlanResourceFromEntityAssembler {

    private WeeklyTrainingPlanResourceFromEntityAssembler() {}

    public static WeeklyTrainingPlanResource toResourceFromEntity(WeeklyTrainingPlan entity,
                                                                  Map<Long, String> exerciseNames) {
        var workouts = entity.workoutsView().stream()
                .map(workout -> toWorkoutResource(workout, exerciseNames))
                .toList();

        return new WeeklyTrainingPlanResource(
                entity.getId(),
                entity.getWeekNumber(),
                entity.getWeekStartDate(),
                entity.getWeekEndDate(),
                entity.getPlanVersion(),
                entity.getParentPlanId(),
                entity.getPlannedDaysCount(),
                entity.getStatus().name(),
                entity.getGenerationSource().name(),
                entity.getModelName(),
                entity.getAdjustmentApplied(),
                entity.getAdjustmentReason(),
                entity.getActivatedAt(),
                workouts);
    }

    public static PlanSummaryResource toSummaryFromEntity(WeeklyTrainingPlan entity) {
        return new PlanSummaryResource(
                entity.getId(),
                entity.getWeekNumber(),
                entity.getWeekStartDate(),
                entity.getWeekEndDate(),
                entity.getPlanVersion(),
                entity.getPlannedDaysCount(),
                entity.getStatus().name(),
                entity.getGenerationSource().name(),
                entity.getAdjustmentApplied());
    }

    public static PlannedWorkoutResource toWorkoutResource(PlannedWorkout workout,
                                                           Map<Long, String> exerciseNames) {
        var exercises = workout.exercisesView().stream()
                .map(exercise -> new PlannedExerciseResource(
                        exercise.getId(),
                        exercise.getExerciseId(),
                        exerciseNames.getOrDefault(exercise.getExerciseId(), null),
                        exercise.getExerciseOrder(),
                        exercise.getPrescriptionType().name(),
                        exercise.getPlannedSets(),
                        exercise.getPlannedReps(),
                        exercise.getPlannedDurationSeconds(),
                        exercise.getTargetLoadKg(),
                        exercise.getRestSeconds(),
                        exercise.getNotes()))
                .toList();

        return new PlannedWorkoutResource(
                workout.getId(),
                workout.getScheduledDate(),
                workout.getFocusCode().name(),
                workout.getWorkoutName(),
                workout.getExpectedDurationMinutes(),
                workout.getDisplayOrder(),
                workout.getStatus().name(),
                workout.getSkipReason() == null ? null : workout.getSkipReason().name(),
                workout.getExpiresAt(),
                exercises);
    }
}
