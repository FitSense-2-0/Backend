package main.web.services.fitsense.iam.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.ZoneId;

/**
 * Zona IANA del usuario. No es opcional: la semana cierra el lunes 03:00 hora
 * local y las fechas de las metricas son locales, no UTC.
 */
@Embeddable
public record UserTimezone(@Column(name = "timezone", nullable = false, length = 64) String value) {

    public static final String DEFAULT = "America/Lima";

    public UserTimezone {
        if (value == null || value.isBlank()) value = DEFAULT;
        try {
            ZoneId.of(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Zona horaria IANA no valida: " + value);
        }
    }

    public static UserTimezone defaultZone() {
        return new UserTimezone(DEFAULT);
    }

    public ZoneId zoneId() {
        return ZoneId.of(value);
    }
}
