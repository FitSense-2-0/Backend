package main.web.services.fitsense.execution.domain.model.valueobjects;

/**
 * Umbrales de 17.2, traidos de calculation_configs.
 * <p>
 * Viajan como parametro hacia el agregado en vez de leerse dentro: el dominio no
 * consulta la base, y ademas asi una sesion siempre se cierra con la version de
 * umbrales vigente en ese momento, que es la que queda registrada.
 */
public record CompletionThresholds(
        double sessionCompletedThresholdPct,
        double sessionValidThresholdPct,
        double exerciseCompletedThresholdPct,
        double completionCapPct
) {
    /** Clasificacion de 17.2: >= 80 COMPLETED, 30 a 79.99 PARTIAL, < 30 SKIPPED. */
    public SessionStatus classify(double completionPct) {
        if (completionPct >= sessionCompletedThresholdPct) return SessionStatus.COMPLETED;
        if (completionPct >= sessionValidThresholdPct) return SessionStatus.PARTIAL;
        return SessionStatus.SKIPPED_LEVEL;
    }
}
