package main.web.services.fitsense.execution.domain.model.valueobjects;

/** Debe coincidir con ck_session_source. */
public enum SessionSource {
    /** La app registro la sesion mientras ocurria. */
    APP_TRACKED,
    /**
     * El usuario declaro despues que ya lo hizo, tipicamente sobre un
     * entrenamiento vencido. Existe para evitar falsos negativos: alguien que
     * entrena y no abre la app no es alguien que no entreno.
     */
    USER_REPORTED
}
