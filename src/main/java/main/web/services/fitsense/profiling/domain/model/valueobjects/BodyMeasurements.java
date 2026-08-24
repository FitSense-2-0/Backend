package main.web.services.fitsense.profiling.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Medidas actuales. El peso se sobrescribe: el MVP no conserva historial
 * (seccion 5). Si mas adelante hace falta la curva de peso, la via es una
 * tabla user_measurements, no una columna adicional aqui.
 */
@Embeddable
public record BodyMeasurements(
        @Column(name = "height_cm", nullable = false, precision = 5, scale = 2) BigDecimal heightCm,
        @Column(name = "current_weight_kg", nullable = false, precision = 6, scale = 2) BigDecimal currentWeightKg,
        @Column(name = "target_weight_kg", precision = 6, scale = 2) BigDecimal targetWeightKg
) {
    private static final BigDecimal MIN_HEIGHT = new BigDecimal("120");
    private static final BigDecimal MAX_HEIGHT = new BigDecimal("250");
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("30");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("300");

    public BodyMeasurements {
        requireInRange(heightCm, MIN_HEIGHT, MAX_HEIGHT, "La estatura", "cm");
        requireInRange(currentWeightKg, MIN_WEIGHT, MAX_WEIGHT, "El peso actual", "kg");
        if (targetWeightKg != null)
            requireInRange(targetWeightKg, MIN_WEIGHT, MAX_WEIGHT, "El peso objetivo", "kg");
    }

    private static void requireInRange(BigDecimal value, BigDecimal min, BigDecimal max,
                                       String label, String unit) {
        if (value == null)
            throw new IllegalArgumentException(label + " es obligatorio.");
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0)
            throw new IllegalArgumentException(
                    "%s debe estar entre %s y %s %s.".formatted(label, min, max, unit));
    }

    /** Solo informativo. No es una metrica del estudio. */
    public BigDecimal bodyMassIndex() {
        var heightM = heightCm.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
        return currentWeightKg.divide(heightM.multiply(heightM), 2, java.math.RoundingMode.HALF_UP);
    }
}
