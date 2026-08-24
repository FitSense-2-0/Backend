package main.web.services.fitsense.profiling.interfaces.rest.transform;

import main.web.services.fitsense.profiling.domain.model.commands.UpdateUserProfileCommand;
import main.web.services.fitsense.profiling.interfaces.rest.resources.UpdateUserProfileResource;

public class UpdateUserProfileCommandFromResourceAssembler {

    private UpdateUserProfileCommandFromResourceAssembler() {}

    public static UpdateUserProfileCommand toCommandFromResource(Long userId,
                                                                 UpdateUserProfileResource resource) {
        return new UpdateUserProfileCommand(
                userId,
                resource.biologicalSex(),
                resource.heightCm(),
                resource.currentWeightKg(),
                resource.targetWeightKg(),
                resource.fitnessLevel(),
                resource.goalType(),
                resource.goalText(),
                resource.trainingLocation(),
                resource.daysPerWeek(),
                resource.availableDays(),
                resource.sessionMinutes(),
                resource.equipmentCodes(),
                resource.blockedExerciseIds(),
                resource.healthNotes()
        );
    }
}
