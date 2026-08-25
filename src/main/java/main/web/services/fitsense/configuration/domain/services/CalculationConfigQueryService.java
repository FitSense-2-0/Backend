package main.web.services.fitsense.configuration.domain.services;

import main.web.services.fitsense.configuration.domain.model.aggregates.CalculationConfig;
import main.web.services.fitsense.configuration.domain.model.queries.GetActiveCalculationConfigQuery;

import java.util.Optional;

public interface CalculationConfigQueryService {
    Optional<CalculationConfig> handle(GetActiveCalculationConfigQuery query);
}
