package main.web.services.fitsense.catalog.interfaces.rest.transform;

import main.web.services.fitsense.catalog.domain.model.aggregates.BodyPart;
import main.web.services.fitsense.catalog.interfaces.rest.resources.BodyPartResource;

public class BodyPartResourceFromEntityAssembler {

    private BodyPartResourceFromEntityAssembler() {}

    public static BodyPartResource toResourceFromEntity(BodyPart entity) {
        return new BodyPartResource(entity.getId(), entity.getCode(),
                entity.getNameEs(), entity.getDisplayOrder());
    }
}
