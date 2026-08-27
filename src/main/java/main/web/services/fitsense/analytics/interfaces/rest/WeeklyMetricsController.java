package main.web.services.fitsense.analytics.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import main.web.services.fitsense.analytics.domain.exceptions.WeeklyMetricsNotFoundException;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsByWeekQuery;
import main.web.services.fitsense.analytics.domain.model.queries.GetWeeklyMetricsHistoryQuery;
import main.web.services.fitsense.analytics.domain.services.WeeklyUserMetricsQueryService;
import main.web.services.fitsense.analytics.interfaces.rest.resources.WeeklyMetricsResource;
import main.web.services.fitsense.analytics.interfaces.rest.transform.WeeklyMetricsResourceFromEntityAssembler;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Solo lectura. Las metricas no se envian desde el cliente: las produce el
 * cierre semanal a partir de lo planificado y lo ejecutado. Un endpoint de
 * escritura aqui permitiria alterar los datos del estudio.
 */
@RestController
@RequestMapping(value = "/api/v1/users/me/metrics", produces = "application/json")
@Tag(name = "Metricas", description = "Adherencia semanal y riesgo de abandono")
public class WeeklyMetricsController {

    private final WeeklyUserMetricsQueryService queryService;

    public WeeklyMetricsController(WeeklyUserMetricsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/weekly")
    @Operation(summary = "Historial semanal de metricas del usuario autenticado")
    public ResponseEntity<List<WeeklyMetricsResource>> history(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var resources = queryService.handle(new GetWeeklyMetricsHistoryQuery(principal.getUserId()))
                .stream()
                .map(WeeklyMetricsResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/weekly/{weekStartDate}")
    @Operation(summary = "Metricas de una semana concreta, por su lunes")
    public ResponseEntity<WeeklyMetricsResource> week(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {

        // Se normaliza al lunes: pedir un miercoles devuelve su semana en vez
        // de un 404 que parece un fallo de datos.
        var monday = TrainingWeek.containing(weekStartDate).startDate();

        return queryService.handle(new GetWeeklyMetricsByWeekQuery(principal.getUserId(), monday))
                .map(WeeklyMetricsResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new WeeklyMetricsNotFoundException(principal.getUserId(), monday));
    }
}
