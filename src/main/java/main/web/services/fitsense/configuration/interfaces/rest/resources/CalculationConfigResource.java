package main.web.services.fitsense.configuration.interfaces.rest.resources;

import main.web.services.fitsense.configuration.domain.model.valueobjects.CalculationParams;

import java.time.OffsetDateTime;

public record CalculationConfigResource(
        Short configId,
        String version,
        String description,
        CalculationParams params,
        OffsetDateTime validFrom
) {}
