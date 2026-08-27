package main.web.services.fitsense.execution.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FinishSessionResource(
        @Min(value = 1, message = "El esfuerzo percibido va de 1 a 10.")
        @Max(value = 10, message = "El esfuerzo percibido va de 1 a 10.")
        @Schema(example = "7", description = "Esfuerzo percibido de la sesion completa")
        Short sessionRpe,

        @Min(value = 1, message = "La satisfaccion va de 1 a 5.")
        @Max(value = 5, message = "La satisfaccion va de 1 a 5.")
        @Schema(example = "4") Short satisfaction,

        @Min(value = 0, message = "Los minutos activos no pueden ser negativos.")
        @Schema(example = "42", description = "Si se omite se calcula con el reloj de la sesion")
        Short activeMinutes
) {}
