package main.web.services.fitsense.adaptation.application.internal.queryservices;

import main.web.services.fitsense.adaptation.domain.model.aggregates.UserIntervention;
import main.web.services.fitsense.adaptation.domain.model.queries.GetInterventionByMetricQuery;
import main.web.services.fitsense.adaptation.domain.model.queries.GetInterventionHistoryQuery;
import main.web.services.fitsense.adaptation.domain.services.UserInterventionQueryService;
import main.web.services.fitsense.adaptation.infrastructure.persistence.jpa.repositories.UserInterventionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserInterventionQueryServiceImpl implements UserInterventionQueryService {

    private final UserInterventionRepository interventionRepository;

    public UserInterventionQueryServiceImpl(UserInterventionRepository interventionRepository) {
        this.interventionRepository = interventionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserIntervention> handle(GetInterventionHistoryQuery query) {
        return interventionRepository.findByUserIdOrderByAppliedAtDesc(query.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserIntervention> handle(GetInterventionByMetricQuery query) {
        return interventionRepository.findByWeeklyMetricId(query.weeklyMetricId());
    }
}
