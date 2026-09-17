package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Un ejercicio del conjunto elegible, traducido al lenguaje de planning.
 * <p>
 * No se usa el tipo del catalogo a proposito: si manana el catalogo agrega
 * campos, el cambio se absorbe en ExternalCatalogService y no llega al
 * generador ni al validador.
 */
public record CandidateExercise(
        Long exerciseId,
        String name,
        String bodyPartCode,
        String equipmentCode,
        int difficulty,
        PrescriptionType defaultPrescription,
        /**
         * Musculo objetivo del catalogo (biceps, triceps, pectorals...). Separa lo
         * que body_part mezcla: "upper arms" es tanto biceps como triceps, y
         * PUSH solo admite triceps y PULL solo biceps.
         */
        String targetMuscle
) {
    public CandidateExercise(Long exerciseId, String name, String bodyPartCode, String equipmentCode,
                             int difficulty, PrescriptionType defaultPrescription) {
        this(exerciseId, name, bodyPartCode, equipmentCode, difficulty, defaultPrescription, null);
    }
}