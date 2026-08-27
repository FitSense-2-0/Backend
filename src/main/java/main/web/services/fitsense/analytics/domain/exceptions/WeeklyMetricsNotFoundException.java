package main.web.services.fitsense.analytics.domain.exceptions;

import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;

import java.time.LocalDate;

public class WeeklyMetricsNotFoundException extends ResourceNotFoundException {
    public WeeklyMetricsNotFoundException(Long userId, LocalDate weekStartDate) {
        super("No hay metricas calculadas para el usuario %d en la semana del %s."
                .formatted(userId, weekStartDate));
    }
}
