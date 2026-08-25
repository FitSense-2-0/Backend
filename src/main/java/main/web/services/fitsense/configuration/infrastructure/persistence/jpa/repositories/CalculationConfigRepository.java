package main.web.services.fitsense.configuration.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.configuration.domain.model.aggregates.CalculationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CalculationConfigRepository extends JpaRepository<CalculationConfig, Short> {

    /** ux_config_active garantiza en la base que hay como maximo una. */
    Optional<CalculationConfig> findByActiveTrue();

    Optional<CalculationConfig> findByVersion(String version);
}
