package main.web.services.fitsense.planning.application.internal.commandservices;

import main.web.services.fitsense.execution.interfaces.acl.WorkoutResultView;
import main.web.services.fitsense.planning.application.internal.outboundservices.acl.ExternalCatalogService;
import main.web.services.fitsense.planning.application.internal.outboundservices.acl.ExternalExecutionService;
import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.valueobjects.PreviousWeekSummary;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.PlannedWorkoutRepository;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.WeeklyTrainingPlanRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma el bloque previous_week: lo que se prescribio la semana anterior y lo
 * que la persona hizo de verdad, ejercicio por ejercicio.
 * <p>
 * Lo hecho sale del intento que cuenta para la adherencia. Si se leyera otro
 * intento, la IA y la metrica verian datos distintos de la misma semana.
 */
@Component
public class PreviousWeekAssembler {

    private final WeeklyTrainingPlanRepository planRepository;
    private final PlannedWorkoutRepository plannedWorkoutRepository;
    private final ExternalCatalogService externalCatalogService;
    private final ExternalExecutionService externalExecutionService;

    public PreviousWeekAssembler(WeeklyTrainingPlanRepository planRepository,
                                 PlannedWorkoutRepository plannedWorkoutRepository,
                                 ExternalCatalogService externalCatalogService,
                                 ExternalExecutionService externalExecutionService) {
        this.planRepository = planRepository;
        this.plannedWorkoutRepository = plannedWorkoutRepository;
        this.externalCatalogService = externalCatalogService;
        this.externalExecutionService = externalExecutionService;
    }

    public PreviousWeekSummary assemble(Long userId, LocalDate previousWeekStart,
                                        int durationToRepsDivisor,
                                        BigDecimal adherencePct, BigDecimal averageRpe) {
        var plans = planRepository.findByUserIdAndWeekStartDateOrderByPlanVersionAsc(
                userId, previousWeekStart);
        if (plans.isEmpty()) return PreviousWeekSummary.empty();

        // Ultima version del plan. LIMITACION: si el plan se reemplazo a mitad
        // de semana, lo registrado sobre entrenamientos de la version anterior
        // (REPLACED) no entra aqui.
        var plan = plans.get(plans.size() - 1);

        int totalVolume = plan.equivalentVolume(durationToRepsDivisor);

        var activeWorkouts = plan.workoutsView().stream()
                .filter(workout -> workout.getStatus() != WorkoutStatus.REPLACED)
                .toList();

        var names = externalCatalogService.fetchNames(activeWorkouts.stream()
                .flatMap(workout -> workout.exercisesView().stream())
                .map(exercise -> exercise.getExerciseId())
                .collect(Collectors.toSet()));

        var results = externalExecutionService.fetchResultsByWorkout(activeWorkouts.stream()
                .map(PlannedWorkout::getId)
                .toList());

        var bodyPartDistribution = new HashMap<String, Integer>();
        var workouts = new ArrayList<PreviousWeekSummary.WorkoutOutcome>();
        var prescriptions = new ArrayList<PreviousWeekSummary.PrescriptionOutcome>();

        for (var workout : activeWorkouts) {
            bodyPartDistribution.merge(workout.getFocusCode().name(), 1, Integer::sum);

            var result = results.get(workout.getId());
            workouts.add(toWorkoutOutcome(workout, result));

            Map<Long, WorkoutResultView.ExerciseResult> byPlannedExercise = result == null
                    ? Map.of()
                    : result.exercises().stream().collect(Collectors.toMap(
                    WorkoutResultView.ExerciseResult::plannedExerciseId,
                    Function.identity(), (first, second) -> first));

            for (var exercise : workout.exercisesView()) {
                var done = byPlannedExercise.get(exercise.getId());
                prescriptions.add(new PreviousWeekSummary.PrescriptionOutcome(
                        workout.getScheduledDate(),
                        exercise.getExerciseId(),
                        names.getOrDefault(exercise.getExerciseId(), null),
                        exercise.getPrescriptionType() == null ? null : exercise.getPrescriptionType().name(),
                        exercise.getPlannedSets(),
                        exercise.getPlannedReps(),
                        exercise.getPlannedDurationSeconds(),
                        exercise.getTargetLoadKg(),
                        done == null ? null : done.actualSets(),
                        done == null ? null : done.actualRepsTotal(),
                        done == null ? null : done.actualDurationSeconds(),
                        done == null ? null : done.actualLoadKg(),
                        done == null ? null : done.completionPercentage(),
                        done == null || done.status() == null
                                ? PreviousWeekSummary.NOT_RECORDED : done.status(),
                        done == null ? null : done.skipReason(),
                        workout.getStatus().name()));
            }
        }

        var usedLast7Days = plannedWorkoutRepository.findExerciseIdsUsedBetween(
                userId, previousWeekStart, previousWeekStart.plusDays(7));

        return new PreviousWeekSummary(adherencePct, averageRpe, totalVolume,
                bodyPartDistribution, List.copyOf(workouts), List.copyOf(prescriptions),
                List.copyOf(usedLast7Days));
    }

    private static PreviousWeekSummary.WorkoutOutcome toWorkoutOutcome(PlannedWorkout workout,
                                                                       WorkoutResultView result) {
        return new PreviousWeekSummary.WorkoutOutcome(
                workout.getScheduledDate(),
                workout.getFocusCode().name(),
                workout.getStatus().name(),
                workout.getSkipReason() == null ? null : workout.getSkipReason().name(),
                result != null,
                result == null ? null : result.sessionStatus(),
                result == null ? null : result.sessionRpe(),
                result == null ? null : result.completionPercentage());
    }
}