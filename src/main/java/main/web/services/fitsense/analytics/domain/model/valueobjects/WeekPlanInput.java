package main.web.services.fitsense.analytics.domain.model.valueobjects;

import java.time.LocalDate;
import java.util.List;

/** El denominador de 17.4, traducido al lenguaje de analytics. */
public record WeekPlanInput(
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
