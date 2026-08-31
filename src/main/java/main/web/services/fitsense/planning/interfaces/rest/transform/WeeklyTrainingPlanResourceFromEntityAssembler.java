package main.web.services.fitsense.planning.interfaces.rest.transform;

import main.web.services.fitsense.catalog.interfaces.acl.EligibleExerciseView;
import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlanSummaryResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlannedExerciseResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlannedWorkoutResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.WeeklyTrainingPlanResource;

import java.util.Map;

/**
 * El detalle de los ejercicios llega por parametro y no se lee del agregado:
 * planning guarda ids, no nombres ni rutas de media. Resolverlo aqui mantiene
 * el catalogo como unica fuente y evita duplicar texto que puede quedar
 * desfasado.
 */
public class WeeklyTrainingPlanResourceFromEntityAssembler {

    private WeeklyTrainingPlanResourceFromEntityAssembler() {}

    public static WeeklyTrainingPlanResource toResourceFromEntity(
            WeeklyTrainingPlan entity, Map<Long, EligibleExerciseView> catalogo) {

        var workouts = entity.workoutsView().stream()
                .map(workout -> toWorkoutResource(workout, catalogo))
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

    public static PlannedWorkoutResource toWorkoutResource(
            PlannedWorkout workout, Map<Long, EligibleExerciseView> catalogo) {

        var exercises = workout.exercisesView().stream()
                .map(exercise -> {
                    var detalle = catalogo.get(exercise.getExerciseId());
                    return new PlannedExerciseResource(
                            exercise.getId(),
                            exercise.getExerciseId(),
                            detalle == null ? null : detalle.nameEs(),
                            detalle == null ? null : detalle.targetMuscle(),
                            detalle == null ? null : detalle.bodyPartCode(),
                            detalle == null ? null : detalle.equipmentCode(),
                            detalle == null ? 0 : detalle.difficultyLevel(),
                            exercise.getExerciseOrder(),
                            exercise.getPrescriptionType().name(),
                            exercise.getPlannedSets(),
                            exercise.getPlannedReps(),
                            exercise.getPlannedDurationSeconds(),
                            exercise.getTargetLoadKg(),
                            exercise.getRestSeconds(),
                            exercise.getNotes(),
                            detalle == null ? null : detalle.gifPath(),
                            detalle == null ? null : detalle.imagePath(),
                            detalle == null ? null : detalle.mediaAttribution());
                })
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
