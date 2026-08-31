package main.web.services.fitsense.catalog.interfaces.acl;

/**
 * Vista de un ejercicio para los demas contextos.
 * <p>
 * Lleva lo que el generador necesita para elegir y lo que la app necesita para
 * pintarlo. Las instrucciones NO viajan: multiplicarian el tamano del contexto
 * enviado a la IA sin cambiar la seleccion, y la app las pide aparte con
 * GET /api/v1/exercises/{id}.
 */
public record EligibleExerciseView(
        Long exerciseId,
        String nameEs,
        String bodyPartCode,
        String equipmentCode,
        String targetMuscle,
        short difficultyLevel,
        String defaultPrescription,
        String gifPath,
        String imagePath,
        String mediaAttribution
) {
    public boolean isDurationBased() {
        return "DURATION".equals(defaultPrescription);
    }
}
