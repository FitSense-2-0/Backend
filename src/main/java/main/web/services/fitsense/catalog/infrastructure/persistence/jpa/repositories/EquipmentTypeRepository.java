package main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.catalog.domain.model.aggregates.EquipmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Short> {
    List<EquipmentType> findAllByOrderByDisplayOrderAsc();
    Optional<EquipmentType> findByCode(String code);
}
