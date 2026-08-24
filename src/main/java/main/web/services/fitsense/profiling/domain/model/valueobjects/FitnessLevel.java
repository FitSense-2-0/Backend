package main.web.services.fitsense.profiling.domain.model.valueobjects;

/** Nivel declarado. El entero es el techo de difficulty_level del catalogo (seccion 20.3). */
public enum FitnessLevel {
    BEGINNER(1),
    INTERMEDIATE(2),
    ADVANCED(3);

    private final int maxDifficultyLevel;

    FitnessLevel(int maxDifficultyLevel) {
        this.maxDifficultyLevel = maxDifficultyLevel;
    }

    public int maxDifficultyLevel() {
        return maxDifficultyLevel;
    }
}
