package main.web.services.fitsense.analytics.interfaces.rest.transform;

import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import main.web.services.fitsense.analytics.interfaces.rest.resources.WeeklyMetricsResource;

public class WeeklyMetricsResourceFromEntityAssembler {

    private WeeklyMetricsResourceFromEntityAssembler() {}

    public static WeeklyMetricsResource toResourceFromEntity(WeeklyUserMetrics entity) {
        return new WeeklyMetricsResource(
                entity.getId(),
                entity.getPlanId(),
                entity.getWeekNumber(),
                entity.getWeekStartDate(),
                entity.getWeekEndDate(),
                entity.isHasActivePlan(),
                entity.getScheduledWorkouts(),
                entity.getValidWorkouts(),
                entity.getCompletedWorkouts(),
                entity.getSkippedWorkouts(),
                entity.getAssignedExercises(),
                entity.getCompletedExercises(),
                entity.getWeightedAdherencePct(),
                entity.getFrequencyAdherencePct(),
                entity.getWorkoutAdherencePct(),
                entity.getExerciseAdherencePct(),
                entity.getTotalTrainingMinutes(),
                entity.getAverageSessionRpe(),
                entity.getAverageSatisfaction(),
                entity.getConsecutiveSkips(),
                entity.getDaysSinceLastWorkout(),
                entity.getDominantSkipReason() == null ? null : entity.getDominantSkipReason().name(),
                entity.getRiskScore(),
                entity.getRiskLevel() == null ? null : entity.getRiskLevel().name(),
                entity.isDropout(),
                entity.getCalculationVersion(),
                entity.getCalculatedAt());
    }
}
