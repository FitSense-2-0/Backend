package main.web.services.fitsense.catalog.interfaces.acl;

/**
 * Vista minima de un ejercicio para los demas contextos. Lleva solo lo que el
 * generador necesita para elegir y lo que el prompt necesita para nombrarlo:
 * ni instrucciones ni media, que multiplicarian el tamano del contexto enviado
 * a la IA sin cambiar la seleccion.
 */
public record EligibleExerciseView(
        Long exerciseId,
        String nameEs,
        String bodyPartCode,
        String equipmentCode,
        String targetMuscle,
        short difficultyLevel,
        String defaultPrescription
) {
    public boolean isDurationBased() {
        return "DURATION".equals(defaultPrescription);
    }
}
