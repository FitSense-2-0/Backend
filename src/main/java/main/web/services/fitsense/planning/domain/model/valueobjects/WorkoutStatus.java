package main.web.services.fitsense.planning.domain.model.valueobjects;

/** Debe coincidir con ck_pw_status. */
public enum WorkoutStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    PARTIAL,
    SKIPPED,
    REPLACED;

    public boolean isFinished() {
        return this == COMPLETED || this == PARTIAL || this == SKIPPED;
    }
}
