package main.web.services.fitsense.catalog.interfaces.rest.transform;

import main.web.services.fitsense.catalog.domain.model.aggregates.EquipmentType;
import main.web.services.fitsense.catalog.interfaces.rest.resources.EquipmentTypeResource;

public class EquipmentTypeResourceFromEntityAssembler {

    private EquipmentTypeResourceFromEntityAssembler() {}

    public static EquipmentTypeResource toResourceFromEntity(EquipmentType entity) {
        return new EquipmentTypeResource(entity.getId(), entity.getCode(), entity.getNameEs(),
                entity.isRequiresGym(), entity.getDisplayOrder());
    }
}
