package main.web.services.fitsense.execution.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

import java.math.BigDecimal;

/**
 * Se envia el total de repeticiones, no serie por serie: es lo que se compara
 * contra lo indicado y lo unico que el estudio necesita.
 */
public record RecordExerciseResource(
        @Min(value = 0, message = "Las series no pueden ser negativas.")
        @Schema(example = "3") Short actualSets,

        @Min(value = 0, message = "Las repeticiones no pueden ser negativas.")
        @Schema(example = "30", description = "Suma de repeticiones de todas las series")
        Integer actualRepsTotal,

        @Min(value = 0, message = "La duracion no puede ser negativa.")
        @Schema(example = "45") Integer actualDurationSeconds,

        @Schema(example = "20.5") BigDecimal actualLoadKg,

        @Schema(example = "FATIGUE", description = "Solo si no lo hiciste o lo dejaste a medias")
        SkipReason skipReason
) {}
