package main.web.services.fitsense.profiling.domain.model.valueobjects;

import java.time.DayOfWeek;

/** Utilidades de conversion: el diseno numera 1 lunes ... 7 domingo (ISO-8601). */
public final class WeekDay {

    public static final short MONDAY = 1;
    public static final short SUNDAY = 7;

    private WeekDay() {}

    public static void requireValid(short day) {
        if (day < MONDAY || day > SUNDAY)
            throw new IllegalArgumentException(
                    "Los dias van de 1 (lunes) a 7 (domingo). Recibido: " + day);
    }

    public static DayOfWeek toDayOfWeek(short day) {
        requireValid(day);
        return DayOfWeek.of(day);
    }

    public static short fromDayOfWeek(DayOfWeek dayOfWeek) {
        return (short) dayOfWeek.getValue();
    }
}
