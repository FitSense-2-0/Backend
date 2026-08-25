package main.web.services.fitsense.catalog.interfaces.rest.transform;

import main.web.services.fitsense.catalog.domain.model.aggregates.Exercise;
import main.web.services.fitsense.catalog.interfaces.rest.resources.ExerciseDetailResource;
import main.web.services.fitsense.catalog.interfaces.rest.resources.ExerciseSummaryResource;

public class ExerciseResourceFromEntityAssembler {

    private ExerciseResourceFromEntityAssembler() {}

    public static ExerciseSummaryResource toSummaryFromEntity(Exercise entity) {
        return new ExerciseSummaryResource(
                entity.getId(),
                entity.displayName(),
                entity.getTargetMuscle(),
                entity.getDifficultyLevel(),
                entity.getDefaultPrescription().name(),
                entity.getImagePath(),
                entity.getGifPath(),
                entity.getMediaAttribution()
        );
    }

    public static ExerciseDetailResource toDetailFromEntity(Exercise entity) {
        return new ExerciseDetailResource(
                entity.getId(),
                entity.getSourceCode(),
                entity.displayName(),
                entity.getNameEn(),
                entity.getTargetMuscle(),
                entity.getSynergistMuscle(),
                entity.secondaryMusclesAsList(),
                entity.getInstructionsEs() != null ? entity.getInstructionsEs() : entity.getInstructionsEn(),
                entity.getDifficultyLevel(),
                entity.getDefaultPrescription().name(),
                entity.getImagePath(),
                entity.getGifPath(),
                entity.getMediaAttribution()
        );
    }
}
