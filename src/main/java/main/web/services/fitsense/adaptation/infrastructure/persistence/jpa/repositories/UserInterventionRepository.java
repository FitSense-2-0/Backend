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

    /**
     * La intervencion que PRODUJO ese plan. Es la forma exacta de encontrar a
     * quien le corresponde el resultado de una semana: la adherencia de un plan
     * puntua a la orden que lo genero, no a la ultima orden que exista.
     */
    Optional<UserIntervention> findByResultingPlanId(Long resultingPlanId);
}
