package main.web.services.fitsense.analytics.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.analytics.domain.model.aggregates.WeeklyUserMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyUserMetricsRepository extends JpaRepository<WeeklyUserMetrics, Long> {

    Optional<WeeklyUserMetrics> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    List<WeeklyUserMetrics> findByUserIdOrderByWeekStartDateDesc(Long userId);

    Optional<WeeklyUserMetrics> findFirstByUserIdOrderByWeekStartDateDesc(Long userId);
}
