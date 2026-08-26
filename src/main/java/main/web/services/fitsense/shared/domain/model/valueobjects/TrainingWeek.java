package main.web.services.fitsense.shared.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * La semana del sistema: lunes a domingo, en la zona horaria del usuario.
 * <p>
 * Vive en el kernel porque planning, execution, analytics, adaptation y las
 * tareas programadas deben coincidir exactamente en donde empieza y termina una
 * semana. Si dos contextos calcularan el lunes por su cuenta, una sesion del
 * domingo por la noche podria contarse en semanas distintas segun quien mire.
 */
public record TrainingWeek(LocalDate startDate, LocalDate endDate) {

    public static TrainingWeek containing(LocalDate date) {
        var monday = mondayOf(date);
        return new TrainingWeek(monday, monday.plusDays(6));
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    public TrainingWeek previous() {
        return new TrainingWeek(startDate.minusWeeks(1), endDate.minusWeeks(1));
    }

    public TrainingWeek next() {
        return new TrainingWeek(startDate.plusWeeks(1), endDate.plusWeeks(1));
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
