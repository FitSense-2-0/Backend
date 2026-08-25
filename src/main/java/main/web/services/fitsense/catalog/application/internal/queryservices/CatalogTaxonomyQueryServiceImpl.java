package main.web.services.fitsense.catalog.application.internal.queryservices;

import main.web.services.fitsense.catalog.domain.model.aggregates.BodyPart;
import main.web.services.fitsense.catalog.domain.model.aggregates.EquipmentType;
import main.web.services.fitsense.catalog.domain.model.queries.GetBodyPartsQuery;
import main.web.services.fitsense.catalog.domain.model.queries.GetEquipmentTypesQuery;
import main.web.services.fitsense.catalog.domain.services.CatalogTaxonomyQueryService;
import main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories.BodyPartRepository;
import main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories.EquipmentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogTaxonomyQueryServiceImpl implements CatalogTaxonomyQueryService {

    private final BodyPartRepository bodyPartRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;

    public CatalogTaxonomyQueryServiceImpl(BodyPartRepository bodyPartRepository,
                                           EquipmentTypeRepository equipmentTypeRepository) {
        this.bodyPartRepository = bodyPartRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BodyPart> handle(GetBodyPartsQuery query) {
        return bodyPartRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentType> handle(GetEquipmentTypesQuery query) {
        return equipmentTypeRepository.findAllByOrderByDisplayOrderAsc();
    }
}
