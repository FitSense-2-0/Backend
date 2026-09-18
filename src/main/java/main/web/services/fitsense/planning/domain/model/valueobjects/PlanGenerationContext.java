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
    /**
     * GEN-IN-1.1 (V18, principios P-1.0): input_snapshot suma principles_version,
     * user.equipment_codes y available_exercises[].prescription_type, y cambia
     * constraints.rep_range (por objetivo) por constraints.rep_limits (amplio).
     * <p>
     * GEN-IN-1.2 (principios P-1.1): previous_week pasa de una lista plana de lo
     * prescrito a workouts[] por dia, con RPE de la sesion, y exercises[] con lo
     * prescrito y lo hecho (actual_*, completion_pct, exercise_status,
     * skip_reason).
     * <p>
     * P-1.2 no cambia el formato: solo el texto de principios y V11.
     * <p>
     * GEN-IN-1.3 (migracion V20): constraints suma min_session_minutes y
     * max_rest_seconds, y rep_limits suma min_reps_by_body_part.
     * <p>
     * GEN-IN-1.4: suggested_split (fecha y enfoque por sesion) y target_muscle en
     * cada ejercicio; days_per_week ya viene con el tope por nivel.
     * <p>
     * GEN-IN-1.5: reduce_volume_caps y min_reps_for_level en constraints.
     * <p>
     * GEN-IN-1.8: cada suggested_split suma body_parts, min_body_parts y
     * max_per_body_part (las cuotas de V15, ya calculadas).
     * <p>
     * GEN-IN-1.7: constraints suma target_reps, target_sets y
     * min_exercises_per_session.
     * <p>
     * GEN-IN-1.6: min_reps calculado en cada available_exercise (zona y nivel,
     * lo mismo que verifica V16); constraints.rep_limits pasa a max_reps y se
     * retira min_reps_for_level; se suma prompt_version.
     * <p>
     * Los snapshots antiguos siguen siendo legibles con su version.
     */
    public static final String SCHEMA_VERSION = "GEN-IN-1.8";

    /**
     * Dificultad maxima efectiva: la del perfil, salvo que el ajuste ordene
     * LOWER_DIFFICULTY, que baja un nivel con piso en 1 (20.5).
     */
    public int effectiveMaxDifficulty() {
        if (adjustment != null && adjustment.maxDifficultyLevel() != null)
            return Math.max(1, adjustment.maxDifficultyLevel());
        return profile.maxDifficultyLevel();
    }

    /** Tope de sesiones de fuerza por semana segun nivel (division semanal, P3). */
    public static final int MAX_SESSIONS_BEGINNER = 4;
    public static final int MAX_SESSIONS_OTHERS = 6;

    /**
     * Sesiones de la semana: las del perfil (o las forzadas por REDUCE_DAYS),
     * con tope por nivel. ACSM 2009 recomienda 2-3 dias para principiantes y
     * nadie recibe 7 dias de fuerza sin descanso. Una principiante que marca 6
     * dias recibe 4; la adherencia se mide contra lo planificado, asi que no la
     * penaliza.
     */
    public int effectiveDaysPerWeek() {
        int requested = adjustment != null && adjustment.forcedDaysPerWeek() != null
                ? adjustment.forcedDaysPerWeek() : profile.daysPerWeek();
        int cap = "BEGINNER".equals(profile.fitnessLevel()) ? MAX_SESSIONS_BEGINNER : MAX_SESSIONS_OTHERS;
        return Math.min(requested, cap);
    }

    /** Division sugerida para la semana, igual para IA, motor de reglas y validador. */
    public java.util.List<main.web.services.fitsense.planning.domain.services.WeeklySplitPlanner.PlannedSession>
    suggestedSplit() {
        int sessions = Math.min(effectiveDaysPerWeek(), profile.availableDays().size());
        return main.web.services.fitsense.planning.domain.services.WeeklySplitPlanner.plan(
                weekStartDate, profile.availableDays(), sessions, weekNumber);
    }

    public int effectiveSessionMinutes() {
        if (adjustment != null && adjustment.forcedSessionMinutes() != null)
            return adjustment.forcedSessionMinutes();
        return profile.sessionMinutes();
    }
}