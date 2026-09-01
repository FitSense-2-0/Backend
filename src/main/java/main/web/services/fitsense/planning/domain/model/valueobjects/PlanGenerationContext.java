package main.web.services.fitsense.planning.domain.model.valueobjects;

import main.web.services.fitsense.configuration.domain.model.valueobjects.PrescriptionParams;

import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que el generador recibe, sea la IA o el motor de reglas.
 * <p>
 * Se serializa a input_snapshot con el formato exacto de 19.1: si un plan se
 * cuestiona meses despues, esta estructura es la evidencia de con que datos se
 * produjo, y permite reproducir la generacion.
 */
public record PlanGenerationContext(
        Long userId,
        short weekNumber,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        PlanningProfile profile,
        PlanAdjustment adjustment,
        PreviousWeekSummary previousWeek,
        List<CandidateExercise> availableExercises,
        SafetyProfile safety,
        PrescriptionParams prescription
) {
    public static final String SCHEMA_VERSION = "GEN-IN-1.0";

    /**
     * Dificultad maxima efectiva: la del perfil, salvo que el ajuste ordene
     * LOWER_DIFFICULTY, que baja un nivel con piso en 1 (20.5).
     */
    public int effectiveMaxDifficulty() {
        if (adjustment != null && adjustment.maxDifficultyLevel() != null)
            return Math.max(1, adjustment.maxDifficultyLevel());
        return profile.maxDifficultyLevel();
    }

    public int effectiveDaysPerWeek() {
        if (adjustment != null && adjustment.forcedDaysPerWeek() != null)
            return adjustment.forcedDaysPerWeek();
        return profile.daysPerWeek();
    }

    public int effectiveSessionMinutes() {
        if (adjustment != null && adjustment.forcedSessionMinutes() != null)
            return adjustment.forcedSessionMinutes();
        return profile.sessionMinutes();
    }
}
