package main.web.services.fitsense.planning.interfaces.acl;

/**
 * Volumen de una semana antes de mezclarlo (V17): repeticiones por un lado,
 * segundos por otro. El equivalente se sigue calculando aparte con el divisor
 * vigente; esto son los ingredientes.
 */
public record WeekVolumeBreakdown(int reps, int seconds) {
    public static WeekVolumeBreakdown empty() {
        return new WeekVolumeBreakdown(0, 0);
    }
}