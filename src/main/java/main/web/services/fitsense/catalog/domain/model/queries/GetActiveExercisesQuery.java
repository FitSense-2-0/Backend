package main.web.services.fitsense.catalog.domain.model.queries;

/**
 * Filtro del catalogo para la app. No es el conjunto elegible del generador:
 * ese lo resuelve GetEligibleExercisesQuery con las restricciones del perfil.
 */
public record GetActiveExercisesQuery(
        String bodyPartCode,
        String equipmentCode,
        Short maxDifficultyLevel,
        String search,
        int limit
) {
    public GetActiveExercisesQuery {
        if (limit <= 0 || limit > 200) limit = 50;
    }

    public static GetActiveExercisesQuery of(String bodyPartCode, String equipmentCode,
                                             Short maxDifficultyLevel, String search, Integer limit) {
        return new GetActiveExercisesQuery(
                blankToNull(bodyPartCode), blankToNull(equipmentCode),
                maxDifficultyLevel, blankToNull(search), limit == null ? 50 : limit);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }
}
