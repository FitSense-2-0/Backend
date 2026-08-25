package main.web.services.fitsense.configuration.application.internal.queryservices;

import main.web.services.fitsense.configuration.domain.model.aggregates.CalculationConfig;
import main.web.services.fitsense.configuration.domain.model.queries.GetActiveCalculationConfigQuery;
import main.web.services.fitsense.configuration.domain.services.CalculationConfigQueryService;
import main.web.services.fitsense.configuration.infrastructure.persistence.jpa.repositories.CalculationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CalculationConfigQueryServiceImpl implements CalculationConfigQueryService {

    private final CalculationConfigRepository calculationConfigRepository;

    public CalculationConfigQueryServiceImpl(CalculationConfigRepository calculationConfigRepository) {
        this.calculationConfigRepository = calculationConfigRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CalculationConfig> handle(GetActiveCalculationConfigQuery query) {
        return calculationConfigRepository.findByActiveTrue();
    }
}
