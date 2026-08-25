package main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.catalog.domain.model.aggregates.BodyPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BodyPartRepository extends JpaRepository<BodyPart, Short> {
    List<BodyPart> findAllByOrderByDisplayOrderAsc();
    Optional<BodyPart> findByCode(String code);
}
