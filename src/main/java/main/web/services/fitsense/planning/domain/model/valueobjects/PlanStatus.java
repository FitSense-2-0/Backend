package main.web.services.fitsense.planning.domain.model.valueobjects;

/** Debe coincidir con ck_plan_status. */
public enum PlanStatus {
    /** Vigente. ux_plan_active garantiza uno solo por usuario y semana. */
    ACTIVE,
    /** La semana termino sin ser reemplazado. */
    COMPLETED,
    /** Lo sustituyo una version posterior. Nunca se edita una semana: se versiona (2.3). */
    REPLACED
}
