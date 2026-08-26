package main.web.services.fitsense.planning.interfaces.acl;

import main.web.services.fitsense.planning.domain.model.commands.RecordWorkoutOutcomeCommand;
import main.web.services.fitsense.planning.domain.model.commands.StartPlannedWorkoutCommand;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.queries.GetPlannedWorkoutByIdQuery;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanCommandService;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanQueryService;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Unico punto de entrada a planning desde otros contextos. En la etapa 1c-1 lo
 * consume execution; en la 1c-2 se le anaden las vistas que necesita analytics
 * para construir el denominador de 17.4.
 */
@Service
public class PlanningContextFacade {

    private final WeeklyTrainingPlanQueryService queryService;
    private final WeeklyTrainingPlanCommandService commandService;

    public PlanningContextFacade(WeeklyTrainingPlanQueryService queryService,
                                 WeeklyTrainingPlanCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public Optional<PlannedWorkoutView> fetchPlannedWorkout(Long plannedWorkoutId) {
        return queryService.handle(new GetPlannedWorkoutByIdQuery(plannedWorkoutId))
                .map(PlanningContextFacade::toView);
    }

    @Transactional
    public void markWorkoutInProgress(Long plannedWorkoutId) {
        commandService.handle(new StartPlannedWorkoutCommand(plannedWorkoutId));
    }

    @Transactional
    public void recordWorkoutOutcome(Long plannedWorkoutId, String outcome, SkipReason skipReason) {
        commandService.handle(new RecordWorkoutOutcomeCommand(
                plannedWorkoutId, WorkoutStatus.valueOf(outcome), skipReason));
    }

    private static PlannedWorkoutView toView(PlannedWorkout workout) {
        var targets = workout.exercisesView().stream()
                .map(exercise -> new PlannedExerciseTarget(
                        exercise.getId(),
                        exercise.getExerciseId(),
                        exercise.getPrescriptionType().name(),
                        exercise.getPlannedSets(),
                        exercise.getPlannedReps(),
                        exercise.getPlannedDurationSeconds()))
                .toList();

        return new PlannedWorkoutView(
                workout.getId(),
                workout.getPlan().getId(),
                workout.getPlan().getUserId(),
                workout.getScheduledDate(),
                workout.getFocusCode().name(),
                workout.getWorkoutName(),
                workout.getExpectedDurationMinutes(),
                workout.getStatus().name(),
                workout.getExpiresAt(),
                targets);
    }
}
