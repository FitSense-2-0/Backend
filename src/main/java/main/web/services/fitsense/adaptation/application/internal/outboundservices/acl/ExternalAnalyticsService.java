package main.web.services.fitsense.adaptation.application.internal.outboundservices.acl;

import main.web.services.fitsense.analytics.interfaces.acl.AnalyticsContextFacade;
import main.web.services.fitsense.analytics.interfaces.acl.WeeklyMetricsView;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/** Capa anticorrupcion hacia analytics: el disparador del ajuste. */
@Service
public class ExternalAnalyticsService {

    private final AnalyticsContextFacade analyticsContextFacade;

    public ExternalAnalyticsService(AnalyticsContextFacade analyticsContextFacade) {
        this.analyticsContextFacade = analyticsContextFacade;
    }

    public Optional<WeeklyMetricsView> fetchWeek(Long userId, LocalDate weekStartDate) {
        return analyticsContextFacade.fetchWeek(userId, weekStartDate);
    }
}
