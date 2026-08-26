package main.web.services.fitsense.planning.infrastructure.generation.rules;

/**
 * Tabla 20.4: la prescripcion por defecto depende SOLO del objetivo.
 * <p>
 * El nivel de condicion fisica no interviene aqui a proposito: ya filtro que
 * ejercicios son elegibles via difficulty_level. Usarlo tambien para las series
 * lo contaria dos veces.
 */
record Prescription(short sets, short reps, short restSeconds) {

    static Prescription forGoal(String goalType) {
        return switch (String.valueOf(goalType)) {
            case "LOSE_WEIGHT" -> new Prescription((short) 3, (short) 15, (short) 45);
            case "GAIN_MUSCLE" -> new Prescription((short) 4, (short) 10, (short) 75);
            case "INCREASE_STRENGTH" -> new Prescription((short) 4, (short) 6, (short) 120);
            default -> new Prescription((short) 3, (short) 12, (short) 60);
        };
    }
}
