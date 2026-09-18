package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Prescripcion de partida segun el objetivo: series, repeticiones y descanso.
 * <p>
 * Es el mismo dato para los dos generadores. El motor de reglas la aplica tal
 * cual y la IA la recibe en constraints.target_reps / target_sets como punto de
 * partida del que apartarse por ejercicio (principio 2). Antes vivia solo
 * dentro del motor de reglas y la IA no tenia ninguna referencia: en el plan 30
 * puso 3x8 —el minimo— a los 21 ejercicios de la semana.
 * <p>
 * Los valores salen de ACSM 2009: resistencia muscular con repeticiones altas y
 * descanso corto, hipertrofia intermedia, fuerza con repeticiones bajas y
 * descanso largo.
 */
public record TargetPrescription(short sets, short reps, short restSeconds) {

    /** Principiantes: 2 series (se sube a 3 solo para llegar a la duracion minima). */
    public static final short MAX_SETS_BEGINNER = 2;

    public static TargetPrescription forGoal(String goalType) {
        return switch (String.valueOf(goalType)) {
            case "LOSE_WEIGHT" -> new TargetPrescription((short) 3, (short) 15, (short) 45);
            case "GAIN_MUSCLE" -> new TargetPrescription((short) 4, (short) 10, (short) 75);
            case "INCREASE_STRENGTH" -> new TargetPrescription((short) 4, (short) 6, (short) 120);
            default -> new TargetPrescription((short) 3, (short) 12, (short) 60);
        };
    }

    /** Series de partida ya ajustadas al nivel. */
    public short setsFor(String fitnessLevel) {
        return "BEGINNER".equals(fitnessLevel) ? (short) Math.min(sets, MAX_SETS_BEGINNER) : sets;
    }
}