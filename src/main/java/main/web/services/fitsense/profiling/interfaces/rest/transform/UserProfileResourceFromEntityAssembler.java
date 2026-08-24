package main.web.services.fitsense.profiling.interfaces.rest.transform;

import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import main.web.services.fitsense.profiling.interfaces.rest.resources.UserProfileResource;

public class UserProfileResourceFromEntityAssembler {

    private UserProfileResourceFromEntityAssembler() {}

    public static UserProfileResource toResourceFromEntity(UserProfile entity) {
        return new UserProfileResource(
                entity.getUserId(),
                entity.getBirthDate(),
                entity.age(),
                entity.getBiologicalSex() == null ? null : entity.getBiologicalSex().name(),
                entity.getMeasurements().heightCm(),
                entity.getMeasurements().currentWeightKg(),
                entity.getMeasurements().targetWeightKg(),
                entity.getFitnessLevel().name(),
                entity.getGoal().goalType().name(),
                entity.getGoal().goalText(),
                entity.getTrainingLocation().name(),
                entity.getDaysPerWeek(),
                entity.availableDaysAsList(),
                entity.getSessionMinutes(),
                entity.equipmentCodesAsList(),
                entity.blockedExerciseIdsAsList(),
                entity.getHealthNotes()
        );
    }
}
