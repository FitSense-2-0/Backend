package main.web.services.fitsense.execution.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/** "Ya lo hice": registro completo de un entrenamiento, normalmente ya vencido. */
public record ReportWorkoutResource(
        @NotNull(message = "Indica cuando lo hiciste.")
        @Schema(example = "2026-08-20T18:30:00-05:00") OffsetDateTime performedAt,

        @Min(value = 1, message = "El esfuerzo percibido va de 1 a 10.")
        @Max(value = 10, message = "El esfuerzo percibido va de 1 a 10.")
        Short sessionRpe,

        @Min(value = 1, message = "La satisfaccion va de 1 a 5.")
        @Max(value = 5, message = "La satisfaccion va de 1 a 5.")
        Short satisfaction,

        @Min(value = 0, message = "Los minutos activos no pueden ser negativos.")
        Short activeMinutes,

        @NotEmpty(message = "Indica que ejercicios hiciste.")
        @Valid List<ReportedExerciseResource> exercises
) {}
