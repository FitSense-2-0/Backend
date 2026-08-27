package main.web.services.fitsense.planning.interfaces.acl;

import java.time.LocalDate;
import java.util.List;

/**
 * El denominador de la semana, construido segun 17.4.
 * <p>
 * planId null significa que no hubo plan. No es lo mismo que un plan incumplido:
 * la adherencia debe quedar NULL para que los promedios del estudio no la
 * confundan con un cero. Es lo que exige ck_wum_null_when_no_plan.
 */
public record WeekDenominatorView(
        Long planId,
        short weekNumber,
        List<ScheduledWorkout> workouts
) {
    public record ScheduledWorkout(
            Long plannedWorkoutId,
            LocalDate scheduledDate,
            int expectedDurationMinutes,
            int assignedExercises,
            String status,
            String skipReason
    ) {}

    public static WeekDenominatorView none() {
        return new WeekDenominatorView(null, (short) 0, List.of());
    }

    public boolean hasActivePlan() {
        return planId != null && !workouts.isEmpty();
    }

    public int scheduledWorkouts() {
        return workouts.size();
    }

    public int assignedExercises() {
        return workouts.stream().mapToInt(ScheduledWorkout::assignedExercises).sum();
    }
}