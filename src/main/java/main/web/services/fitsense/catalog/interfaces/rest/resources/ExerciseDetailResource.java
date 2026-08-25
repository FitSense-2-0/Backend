package main.web.services.fitsense.catalog.interfaces.rest.resources;

import java.util.List;

public record ExerciseDetailResource(
        Long exerciseId,
        String sourceCode,
        String name,
        String nameEn,
        String targetMuscle,
        String synergistMuscle,
        List<String> secondaryMuscles,
        String instructions,
        short difficultyLevel,
        String defaultPrescription,
        String imagePath,
        String gifPath,
        String mediaAttribution
) {}
