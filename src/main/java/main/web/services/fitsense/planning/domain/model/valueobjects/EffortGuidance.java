package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Indicacion de esfuerzo que ve el participante junto a su plan.
 * <p>
 * El plan prescribe un numero fijo de repeticiones. Ese numero solo es coherente
 * si la persona sabe con que esfuerzo hacerlo: por eso la indicacion viaja con el
 * plan y no queda solo dentro del prompt de la IA.
 * <p>
 * Base: ACSM 2026 propone entrenar cerca del fallo, unas 2-3 repeticiones en
 * reserva, como operacionalizacion practica (no como valor optimo). Las 3 en
 * reserva para principiantes son criterio de diseno (principio 5): un
 * principiante estima peor cuanto le falta para el fallo.
 * <p>
 * DEBE coincidir con los principios 1, 5 y 8 de PrescriptionPrinciples. Si uno
 * cambia, cambia el otro.
 */
public record EffortGuidance(
        String fitnessLevel,
        String repsInReserve,
        String setsRepsText,
        String durationText,
        String loadText
) {
    private static final String DURATION_TEXT =
            "En los ejercicios por tiempo, mantén la posición con buena técnica "
                    + "y termina cuando todavía podrías aguantar unos segundos más.";

    private static final String LOAD_TEXT =
            "El peso sugerido es solo una referencia: elige el peso con el que "
                    + "cumplas las repeticiones dejando ese margen.";

    public static EffortGuidance forLevel(String fitnessLevel) {
        if ("BEGINNER".equals(fitnessLevel)) {
            return new EffortGuidance(fitnessLevel, "3",
                    "Termina cada serie sintiendo que podrías hacer unas 3 repeticiones más, "
                            + "con buena técnica. No hace falta llegar al límite.",
                    DURATION_TEXT, LOAD_TEXT);
        }
        // INTERMEDIATE, ADVANCED o nivel desconocido: la regla general.
        return new EffortGuidance(fitnessLevel, "2-3",
                "Termina cada serie sintiendo que podrías hacer 2 o 3 repeticiones más, "
                        + "con buena técnica. No hace falta llegar al límite.",
                DURATION_TEXT, LOAD_TEXT);
    }
}