package main.web.services.fitsense.execution.application.internal.queryservices;

import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.queries.GetCurrentSessionQuery;
import main.web.services.fitsense.execution.domain.model.queries.GetSessionByIdQuery;
import main.web.services.fitsense.execution.domain.model.queries.GetSessionsByWeekQuery;
import main.web.services.fitsense.execution.domain.model.valueobjects.SessionStatus;
import main.web.services.fitsense.execution.domain.services.WorkoutSessionQueryService;
import main.web.services.fitsense.execution.infrastructure.persistence.jpa.repositories.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class WorkoutSessionQueryServiceImpl implements WorkoutSessionQueryService {

    private final WorkoutSessionRepository sessionRepository;

    public WorkoutSessionQueryServiceImpl(WorkoutSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkoutSession> handle(GetSessionByIdQuery query) {
        return sessionRepository.findById(query.sessionId())
                .filter(session -> session.getUserId().equals(query.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkoutSession> handle(GetCurrentSessionQuery query) {
        return sessionRepository.findByUserIdAndStatus(query.userId(), SessionStatus.IN_PROGRESS);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutSession> handle(GetSessionsByWeekQuery query) {
        var from = OffsetDateTime.of(query.weekStartDate(), LocalTime.MIDNIGHT, ZoneOffset.UTC);
        var to = OffsetDateTime.of(query.weekEndDate().plusDays(1), LocalTime.MIDNIGHT, ZoneOffset.UTC);
        return sessionRepository.findCountedBetween(query.userId(), from, to);
    }
}
