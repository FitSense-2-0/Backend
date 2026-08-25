package main.web.services.fitsense.catalog.domain.model.queries;

import java.util.List;

/**
 * El conjunto elegible: lo unico que el generador puede proponer.
 * <p>
 * Se construye desde el perfil (ubicacion, equipamiento, nivel, bloqueos).
 * Si el generador devuelve un exercise_id fuera de este conjunto, la
 * validacion 1 de la seccion 19 rechaza el plan.
 */
public record GetEligibleExercisesQuery(
        List<String> equipmentCodes,
        boolean excludeGymOnly,
        int maxDifficultyLevel,
        List<Long> blockedExerciseIds
) {}
