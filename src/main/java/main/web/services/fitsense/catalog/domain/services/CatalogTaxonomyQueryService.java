package main.web.services.fitsense.catalog.domain.services;

import main.web.services.fitsense.catalog.domain.model.aggregates.BodyPart;
import main.web.services.fitsense.catalog.domain.model.aggregates.EquipmentType;
import main.web.services.fitsense.catalog.domain.model.queries.GetBodyPartsQuery;
import main.web.services.fitsense.catalog.domain.model.queries.GetEquipmentTypesQuery;

import java.util.List;

public interface CatalogTaxonomyQueryService {
    List<BodyPart> handle(GetBodyPartsQuery query);
    List<EquipmentType> handle(GetEquipmentTypesQuery query);
}
