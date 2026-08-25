package main.web.services.fitsense.catalog.interfaces.rest.resources;

public record ExerciseSummaryResource(
        Long exerciseId,
        String name,
        String targetMuscle,
        short difficultyLevel,
        String defaultPrescription,
        String imagePath,
        String gifPath,
        String mediaAttribution
) {}
