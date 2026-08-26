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
        PrescriptionType defaultPrescription
) {}
