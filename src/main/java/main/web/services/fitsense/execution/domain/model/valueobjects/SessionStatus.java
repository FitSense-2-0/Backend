package main.web.services.fitsense.execution.domain.model.valueobjects;

/** Debe coincidir con ck_session_status. */
public enum SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    PARTIAL,
    ABANDONED,

    /**
     * NO se persiste en workout_sessions: la tabla no lo admite.
     * <p>
     * Existe porque 17.2 clasifica por debajo del 30 % como SKIPPED. Una sesion
     * asi se guarda como PARTIAL —el usuario si abrio la app e hizo algo— pero
     * el entrenamiento del plan se cierra como SKIPPED, que es lo que el
     * denominador de la adherencia necesita saber.
     */
    SKIPPED_LEVEL;

    /** Finalizada. Solo estas cuentan como intento valido. */
    public boolean isFinished() {
        return this == COMPLETED || this == PARTIAL;
    }

    /** Estado persistible en la tabla: SKIPPED_LEVEL se guarda como PARTIAL. */
    public SessionStatus persistable() {
        return this == SKIPPED_LEVEL ? PARTIAL : this;
    }

    /** Desenlace que se refleja en planned_workouts.status. */
    public String workoutOutcome() {
        return switch (this) {
            case COMPLETED -> "COMPLETED";
            case SKIPPED_LEVEL -> "SKIPPED";
            default -> "PARTIAL";
        };
    }
}
