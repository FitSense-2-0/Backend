package main.web.services.fitsense.configuration.domain.model.valueobjects;

import java.util.Map;

/**
 * Bloque "prescription" de calculation_configs.params, anadido en V12 y
 * ampliado en V14 con los parametros de estimacion de duracion.
 * <p>
 * Los rangos de repeticiones por objetivo y el coste temporal de una serie no
 * son constantes del generador: son umbrales de calculo, y como el resto viven
 * en la configuracion versionada. Calibrarlos tras el piloto es insertar una
 * fila nueva, no recompilar, y cada plan queda ligado a la version con la que
 * se produjo.
 */
public record PrescriptionParams(
        Integer sessionMinutesFloorPct,
        Integer secondsPerRep,
        Integer transitionSeconds,
        Integer warmupMinutes,
        Integer defaultRestSeconds,
        Integer durationTolerancePct,
        Map<String, RepRange> byGoal
) {
    /**
     * Rango, no valor exacto: la IA debe poder variar entre ejercicios —un
     * accesorio admite mas repeticiones que un basico— pero no irse a otro
     * objetivo. El valor puntual de la tabla 20.4 lo sigue usando el motor de
     * reglas.
     */
    public record RepRange(Integer minReps, Integer maxReps) {}

    /** Sin rango declarado no se valida: mejor no bloquear que inventar un limite. */
    public RepRange forGoal(String goalType) {
        return byGoal == null ? null : byGoal.get(goalType);
    }

    public int floorPct() {
        return sessionMinutesFloorPct == null ? 70 : sessionMinutesFloorPct;
    }

    // Los valores por defecto replican MVP-1.2. Existen para que una
    // configuracion antigua sin el bloque de duracion no deje al estimador sin
    // parametros: se estima igual y queda registrado con que version se hizo.
    public int secondsPerRepOrDefault()      { return secondsPerRep      == null ? 3  : secondsPerRep; }
    public int transitionSecondsOrDefault()  { return transitionSeconds  == null ? 60 : transitionSeconds; }
    public int warmupMinutesOrDefault()      { return warmupMinutes      == null ? 5  : warmupMinutes; }
    public int defaultRestSecondsOrDefault() { return defaultRestSeconds == null ? 60 : defaultRestSeconds; }
    public int durationTolerancePctOrDefault() {
        return durationTolerancePct == null ? 20 : durationTolerancePct;
    }
}