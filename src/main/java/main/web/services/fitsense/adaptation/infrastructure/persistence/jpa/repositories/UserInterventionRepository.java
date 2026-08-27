package main.web.services.fitsense.adaptation.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.adaptation.domain.model.aggregates.UserIntervention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserInterventionRepository extends JpaRepository<UserIntervention, Long> {

    Optional<UserIntervention> findByWeeklyMetricId(Long weeklyMetricId);

    List<UserIntervention> findByUserIdOrderByAppliedAtDesc(Long userId);

    Optional<UserIntervention> findFirstByUserIdOrderByAppliedAtDesc(Long userId);
}
