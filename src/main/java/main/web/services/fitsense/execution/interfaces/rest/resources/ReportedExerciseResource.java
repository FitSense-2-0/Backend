package main.web.services.fitsense.execution.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

import java.math.BigDecimal;

/**
 * Un ejercicio dentro del reporte retroactivo. Lleva el id del ejercicio
 * planificado porque el reporte envia varios de una vez y no hay ruta por
 * ejercicio donde ponerlo.
 */
public record ReportedExerciseResource(
        @NotNull(message = "Indica a que ejercicio del plan corresponde este registro.")
        Long plannedExerciseId,

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
