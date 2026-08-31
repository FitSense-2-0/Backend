package main.web.services.fitsense.catalog.domain.model.queries;

import java.util.List;

/**
 * El conjunto elegible: lo unico que el generador puede proponer.
 * <p>
 * Si el generador devuelve un exercise_id fuera de este conjunto, la validacion
 * 1 de la seccion 19 rechaza el plan. Por eso las restricciones de seguridad se
 * aplican AQUI y no como instruccion en el prompt: la IA ni siquiera ve lo que
 * no le corresponde al participante.
 */
public record GetEligibleExercisesQuery(
        List<String> equipmentCodes,
        boolean excludeGymOnly,
        int maxDifficultyLevel,
        List<Long> blockedExerciseIds,

        /** Fuera saltos, pliometria y olimpicos. */
        boolean excludeHighImpact,

        /** Fuera lo que exige bajar al suelo y levantarse. Es barrera de movilidad. */
        boolean excludeFloorWork,

        /** Fuera carga axial e invertidos. */
        boolean excludeAxialLoad
) {
    /** Sin restricciones de seguridad. Es el caso de la mayoria de perfiles. */
    public static GetEligibleExercisesQuery unrestricted(List<String> equipmentCodes,
                                                         boolean excludeGymOnly,
                                                         int maxDifficultyLevel,
                                                         List<Long> blockedExerciseIds) {
        return new GetEligibleExercisesQuery(equipmentCodes, excludeGymOnly, maxDifficultyLevel,
                blockedExerciseIds, false, false, false);
    }
}
