package main.web.services.fitsense.planning.interfaces.rest.resources;

import java.math.BigDecimal;

/**
 * Un ejercicio dentro del plan, tal como lo pinta la app.
 * <p>
 * Lleva la media resuelta desde el catalogo: planning guarda solo exercise_id,
 * asi que el nombre, el gif y la atribucion se anaden al construir la respuesta.
 * Sin ellos el front tendria que hacer una llamada por ejercicio.
 */
public record PlannedExerciseResource(
        Long plannedExerciseId,
        Long exerciseId,
        String exerciseName,
        String targetMuscle,
        String bodyPart,
        String equipment,
        short difficultyLevel,
        short exerciseOrder,
        String prescriptionType,
        Short plannedSets,
        Short plannedReps,
        Integer plannedDurationSeconds,
        BigDecimal targetLoadKg,
        Short restSeconds,
        String notes,
        String gifPath,
        String imagePath,
        String mediaAttribution
) {}
