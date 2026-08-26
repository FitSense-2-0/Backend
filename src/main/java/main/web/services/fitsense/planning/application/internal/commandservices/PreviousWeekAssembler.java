package main.web.services.fitsense.planning.application.internal.commandservices;

import main.web.services.fitsense.planning.application.internal.outboundservices.acl.ExternalCatalogService;
import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.valueobjects.PreviousWeekSummary;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.PlannedWorkoutRepository;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.WeeklyTrainingPlanRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Arma el bloque previous_week de 19.1.
 * <p>
 * Es lo que permite el ajuste fino: la IA ve que se prescribio exactamente y
 * cuanto se cumplio de cada ejercicio, asi puede bajar repeticiones donde el
 * cumplimiento fue parcial y sustituir lo que se omitio, en vez de recortar a
 * ciegas. Sin este bloque solo conoceria el porcentaje global.
 * <p>
 * El cumplimiento por ejercicio lo aporta execution; aqui llega ya resuelto para
 * no invertir la dependencia entre contextos.
 */
@Component
public class PreviousWeekAssembler {

    private final WeeklyTrainingPlanRepository planRepository;
    private final PlannedWorkoutRepository plannedWorkoutRepository;
    private final ExternalCatalogService externalCatalogService;

    public PreviousWeekAssembler(WeeklyTrainingPlanRepository planRepository,
                                 PlannedWorkoutRepository plannedWorkoutRepository,
                                 ExternalCatalogService externalCatalogService) {
        this.planRepository = planRepository;
        this.plannedWorkoutRepository = plannedWorkoutRepository;
        this.externalCatalogService = externalCatalogService;
    }

    public PreviousWeekSummary assemble(Long userId, LocalDate previousWeekStart,
                                        int durationToRepsDivisor) {
        var plans = planRepository.findByUserIdAndWeekStartDateOrderByPlanVersionAsc(
                userId, previousWeekStart);
        if (plans.isEmpty()) return PreviousWeekSummary.empty();

        // 17.4: se toma la ultima version, pero los entrenamientos REPLACED se
        // descartan uno a uno, no el plan entero.
        var plan = plans.get(plans.size() - 1);

        int totalVolume = plan.equivalentVolume(durationToRepsDivisor);
        var bodyPartDistribution = new HashMap<String, Integer>();
        var prescriptions = new ArrayList<PreviousWeekSummary.PrescriptionOutcome>();

        var exerciseIds = plan.workoutsView().stream()
                .filter(workout -> workout.getStatus() != WorkoutStatus.REPLACED)
                .flatMap(workout -> workout.exercisesView().stream())
                .map(exercise -> exercise.getExerciseId())
                .collect(Collectors.toSet());
        var names = externalCatalogService.fetchNames(exerciseIds);

        for (var workout : plan.workoutsView()) {
            if (workout.getStatus() == WorkoutStatus.REPLACED) continue;

            bodyPartDistribution.merge(workout.getFocusCode().name(), 1, Integer::sum);

            for (var exercise : workout.exercisesView()) {
                prescriptions.add(new PreviousWeekSummary.PrescriptionOutcome(
                        exercise.getExerciseId(),
                        names.getOrDefault(exercise.getExerciseId(), null),
                        exercise.getPlannedSets(),
                        exercise.getPlannedReps(),
                        exercise.getTargetLoadKg(),
                        null,
                        workout.getStatus().name()));
            }
        }

        var usedLast7Days = plannedWorkoutRepository.findExerciseIdsUsedBetween(
                userId, previousWeekStart, previousWeekStart.plusDays(7));

        return new PreviousWeekSummary(null, null, totalVolume, bodyPartDistribution,
                List.copyOf(prescriptions), List.copyOf(usedLast7Days));
    }
}
