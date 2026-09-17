package main.web.services.fitsense.planning.interfaces.rest.resources;

/**
 * Indicacion de esfuerzo para mostrar en la app junto al plan.
 *
 * @param repsInReserve "3" (principiante) o "2-3"
 * @param setsRepsText  texto para ejercicios por repeticiones
 * @param durationText  texto para ejercicios por tiempo
 * @param loadText      texto para ejercicios con peso sugerido
 */
public record EffortGuidanceResource(
        String repsInReserve,
        String setsRepsText,
        String durationText,
        String loadText
) {}