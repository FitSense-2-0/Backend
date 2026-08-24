package main.web.services.fitsense.profiling.interfaces.rest.transform;

import main.web.services.fitsense.profiling.domain.model.commands.CreateUserProfileCommand;
import main.web.services.fitsense.profiling.interfaces.rest.resources.CreateUserProfileResource;

public class CreateUserProfileCommandFromResourceAssembler {

    private CreateUserProfileCommandFromResourceAssembler() {}

    public static CreateUserProfileCommand toCommandFromResource(Long userId,
                                                                 CreateUserProfileResource resource) {
        return new CreateUserProfileCommand(
                userId,
                resource.birthDate(),
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
