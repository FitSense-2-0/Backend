package main.web.services.fitsense.planning.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

public record SkipWorkoutResource(
        @NotNull(message = "Indica la causa: es lo que permite al sistema ajustar la semana siguiente.")
        @Schema(example = "LACK_OF_TIME", description = "Causa declarada del salto")
        SkipReason reason
) {}
