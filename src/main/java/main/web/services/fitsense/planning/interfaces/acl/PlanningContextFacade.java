package main.web.services.fitsense.planning.interfaces.acl;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.commands.ClosePlanWeekCommand;
import main.web.services.fitsense.planning.domain.model.commands.GenerateWeeklyPlanCommand;
import main.web.services.fitsense.planning.domain.model.commands.RecordWorkoutOutcomeCommand;
import main.web.services.fitsense.planning.domain.model.commands.StartPlannedWorkoutCommand;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.queries.GetPlannedWorkoutByIdQuery;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanAdjustment;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanCommandService;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanQueryService;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.WeeklyTrainingPlanRepository;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unico punto de entrada a planning desde otros contextos: lo consumen
 * execution, analytics, adaptation y la tarea semanal.
 */
@Service
public class PlanningContextFacade {

    private final WeeklyTrainingPlanQueryService queryService;
    private final WeeklyTrainingPlanCommandService commandService;
    private final WeeklyTrainingPlanRepository planRepository;

    public PlanningContextFacade(WeeklyTrainingPlanQueryService queryService,
                                 WeeklyTrainingPlanCommandService commandService,
                                 WeeklyTrainingPlanRepository planRepository) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.planRepository = planRepository;
    }

    /**
     * Denominador de la semana segun 17.4.
     * <p>
     * Recorre TODAS las versiones de la semana, no solo la activa, y descarta
     * los entrenamientos REPLACED uno a uno. Asi los ya ejecutados antes de una
     * intervencion siguen contando aunque su version haya sido reemplazada: si
     * solo se mirara el plan activo, una intervencion a mitad de semana borraria
     * sesiones reales del denominador.
     */
    @Transactional(readOnly = true)
    public WeekDenominatorView fetchWeekDenominator(Long userId, LocalDate weekStartDate) {
        var plans = planRepository.findByUserIdAndWeekStartDateOrderByPlanVersionAsc(
                userId, weekStartDate);
        if (plans.isEmpty()) return WeekDenominatorView.none();

        var workouts = new ArrayList<WeekDenominatorView.ScheduledWorkout>();
        for (var plan : plans) {
            for (var workout : plan.workoutsView()) {
                if (workout.getStatus() == WorkoutStatus.REPLACED) continue;
                workouts.add(new WeekDenominatorView.ScheduledWorkout(
                        workout.getId(),
                        workout.getScheduledDate(),
                        workout.getExpectedDurationMinutes(),
                        workout.assignedExerciseCount(),
                        workout.getStatus().name(),
                        workout.getSkipReason() == null ? null : workout.getSkipReason().name()));
            }
        }

        var last = plans.get(plans.size() - 1);
        return new WeekDenominatorView(last.getId(), last.getWeekNumber(), List.copyOf(workouts));
    }

    /** Volumen en repeticiones equivalentes de la ultima version de una semana (18.1). */
    @Transactional(readOnly = true)
    public int fetchWeekVolume(Long userId, LocalDate weekStartDate, int divisor) {
        var plans = planRepository.findByUserIdAndWeekStartDateOrderByPlanVersionAsc(
                userId, weekStartDate);
        if (plans.isEmpty()) return 0;
        return plans.get(plans.size() - 1).equivalentVolume(divisor);
    }

    /**
     * Volumen de la semana 1 del participante. Es la referencia del tope
     * acumulado de 18.4: ninguna semana puede bajar del 60 % de este numero,
     * por muchas reducciones consecutivas que se acumulen.
     */
    @Transactional(readOnly = true)
    public int fetchBaselineVolume(Long userId, int divisor) {
        return planRepository.findFirstByUserIdAndWeekNumberOrderByPlanVersionAsc(userId, (short) 1)
                .map(plan -> plan.equivalentVolume(divisor))
                .orElse(0);
    }

    /** Cierra la semana: el plan activo pasa a COMPLETED si nadie lo reemplazo. */
    @Transactional
    public void closeWeek(Long userId, LocalDate weekStartDate) {
        commandService.handle(new ClosePlanWeekCommand(userId, weekStartDate));
    }

    /** Genera el plan de la semana nueva con el ajuste ya decidido. */
    @Transactional
    public Optional<Long> generateWeeklyPlan(Long userId, LocalDate weekStartDate,
                                             PlanAdjustment adjustment) {
        return commandService.handle(new GenerateWeeklyPlanCommand(
                        userId, weekStartDate, adjustment, false))
                .map(WeeklyTrainingPlan::getId);
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