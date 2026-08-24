package main.web.services.fitsense.profiling.domain.model.valueobjects;

public enum TrainingLocation {
    HOME,
    GYM,
    MIXED;

    /** Con HOME se excluye todo equipamiento con requires_gym = true. */
    public boolean excludesGymOnlyEquipment() {
        return this == HOME;
    }
}
