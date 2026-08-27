package main.web.services.fitsense.adaptation.domain.services;

import main.web.services.fitsense.adaptation.domain.model.aggregates.UserIntervention;
import main.web.services.fitsense.adaptation.domain.model.queries.GetInterventionByMetricQuery;
import main.web.services.fitsense.adaptation.domain.model.queries.GetInterventionHistoryQuery;

import java.util.List;
import java.util.Optional;

public interface UserInterventionQueryService {
    List<UserIntervention> handle(GetInterventionHistoryQuery query);
    Optional<UserIntervention> handle(GetInterventionByMetricQuery query);
}
