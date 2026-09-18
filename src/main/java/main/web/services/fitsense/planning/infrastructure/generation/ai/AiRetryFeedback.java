package main.web.services.fitsense.planning.infrastructure.generation.ai;

import java.util.List;

/**
 * Lo que recibe un reintento de la IA.
 * <p>
 * Antes solo viajaba la lista de errores y el modelo generaba un plan nuevo
 * desde cero: en el plan 29 corrigio un ejercicio, dejo otros dos que el
 * mensaje nombraba y perdio minutos en otra sesion porque la rehizo entera. Con
 * su propia propuesta a la vista puede CORREGIRLA en vez de volver a sortearla.
 *
 * @param rejectedOutput    JSON de la ultima propuesta rechazada, tal cual lo
 *                          devolvio el modelo. null si no hubo respuesta usable.
 * @param rejectionProblems motivos de rechazo de esa ultima propuesta
 * @param earlierProblems   motivos de intentos anteriores que no estan en la
 *                          lista de arriba (con 3 o mas intentos)
 */
public record AiRetryFeedback(String rejectedOutput,
                              List<String> rejectionProblems,
                              List<String> earlierProblems) {

    public AiRetryFeedback {
        rejectionProblems = rejectionProblems == null ? List.of() : List.copyOf(rejectionProblems);
        earlierProblems = earlierProblems == null ? List.of() : List.copyOf(earlierProblems);
    }

    public static AiRetryFeedback none() {
        return new AiRetryFeedback(null, List.of(), List.of());
    }

    /** Compatibilidad con TrainingPlanGenerator.generate(context, problems). */
    public static AiRetryFeedback ofProblems(List<String> problems) {
        return new AiRetryFeedback(null, problems, List.of());
    }

    public boolean isEmpty() {
        return rejectionProblems.isEmpty() && earlierProblems.isEmpty();
    }
}