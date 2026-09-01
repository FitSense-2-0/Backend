package main.web.services.fitsense.configuration.domain.model.valueobjects;

import java.util.Map;

/**
 * Bloque "prescription" de calculation_configs.params, anadido en V12.
 * <p>
 * Los rangos de repeticiones por objetivo no son constantes del generador: son
 * umbrales de calculo, y como el resto viven en la configuracion versionada.
 * Calibrarlos tras el piloto es insertar una fila nueva, no recompilar, y cada
 * plan queda ligado a la version con la que se produjo.
 */
public record PrescriptionParams(
        Integer sessionMinutesFloorPct,
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
}