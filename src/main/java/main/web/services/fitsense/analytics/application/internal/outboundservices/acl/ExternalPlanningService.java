package main.web.services.fitsense.analytics.application.internal.outboundservices.acl;

import main.web.services.fitsense.analytics.domain.model.valueobjects.WeekPlanInput;
import main.web.services.fitsense.planning.interfaces.acl.PlanningContextFacade;
import main.web.services.fitsense.planning.interfaces.acl.WeekVolumeBreakdown;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Capa anticorrupcion hacia planning: el denominador de 17.4. */
@Service
public class ExternalPlanningService {

    private final PlanningContextFacade planningContextFacade;

    public ExternalPlanningService(PlanningContextFacade planningContextFacade) {
        this.planningContextFacade = planningContextFacade;
    }

    public WeekPlanInput fetchWeekPlan(Long userId, LocalDate weekStartDate) {
        var week = planningContextFacade.fetchWeekDenominator(userId, weekStartDate);

        var workouts = week.workouts().stream()
                .map(workout -> new WeekPlanInput.ScheduledWorkout(
                        workout.plannedWorkoutId(),
                        workout.scheduledDate(),
                        workout.expectedDurationMinutes(),
                        workout.assignedExercises(),
                        workout.status(),
                        workout.skipReason()))
                .toList();

        return new WeekPlanInput(week.planId(), week.weekNumber(), workouts);
    }

    /**
     * Volumen prescrito de la semana (18.1). Es el par fijo del volumen
     * ejecutado: juntos permiten leer si la adherencia subio porque el
     * participante entreno mas o porque el plan pidio menos.
     */
    public int weekVolume(Long userId, LocalDate weekStartDate, int durationToRepsDivisor) {
        return planningContextFacade.fetchWeekVolume(userId, weekStartDate, durationToRepsDivisor);
    }
    /** Componentes crudos del volumen prescrito (V17). */
    public WeekVolumeBreakdown weekBreakdown(Long userId, LocalDate weekStartDate) {
        return planningContextFacade.fetchWeekBreakdown(userId, weekStartDate);
    }
}
