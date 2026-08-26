package main.web.services.fitsense.planning.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import main.web.services.fitsense.planning.application.internal.outboundservices.acl.ExternalCatalogService;
import main.web.services.fitsense.planning.domain.exceptions.PlanNotFoundException;
import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.commands.GenerateWeeklyPlanCommand;
import main.web.services.fitsense.planning.domain.model.commands.SkipPlannedWorkoutCommand;
import main.web.services.fitsense.planning.domain.model.queries.GetActivePlanQuery;
import main.web.services.fitsense.planning.domain.model.queries.GetPlanByIdQuery;
import main.web.services.fitsense.planning.domain.model.queries.GetPlanHistoryQuery;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanCommandService;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanQueryService;
import main.web.services.fitsense.planning.interfaces.rest.resources.PlanSummaryResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.SkipWorkoutResource;
import main.web.services.fitsense.planning.interfaces.rest.resources.WeeklyTrainingPlanResource;
import main.web.services.fitsense.planning.interfaces.rest.transform.WeeklyTrainingPlanResourceFromEntityAssembler;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El plan no se edita nunca por API: no hay PUT ni DELETE (2.2). Solo se lee, se
 * genera la primera semana y se declara que un entrenamiento no se hara.
 * Cualquier cambio del contenido pasa por una version nueva que produce la tarea
 * del lunes.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = "application/json")
@Tag(name = "Planes", description = "Plan semanal generado y sus entrenamientos")
public class TrainingPlanController {

    private final WeeklyTrainingPlanQueryService queryService;
    private final WeeklyTrainingPlanCommandService commandService;
    private final ExternalCatalogService externalCatalogService;

    public TrainingPlanController(WeeklyTrainingPlanQueryService queryService,
                                  WeeklyTrainingPlanCommandService commandService,
                                  ExternalCatalogService externalCatalogService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.externalCatalogService = externalCatalogService;
    }

    @GetMapping("/users/me/plan/current")
    @Operation(summary = "Devuelve el plan vigente del usuario autenticado")
    public ResponseEntity<WeeklyTrainingPlanResource> current(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        return queryService.handle(new GetActivePlanQuery(principal.getUserId(), LocalDate.now()))
                .map(this::toResource)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> PlanNotFoundException.active(principal.getUserId()));
    }

    /**
     * Primera semana. En produccion las siguientes las genera la tarea del lunes;
     * este endpoint falla si ya hay un plan activo en vez de crear un segundo,
     * porque dos planes activos dejarian la semana con dos denominadores.
     */
    @PostMapping("/users/me/plan/generate")
    @Operation(summary = "Genera el plan de esta semana. Falla si ya existe uno activo.")
    public ResponseEntity<WeeklyTrainingPlanResource> generate(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var monday = TrainingWeek.containing(LocalDate.now()).startDate();
        return commandService.handle(GenerateWeeklyPlanCommand.firstPlan(principal.getUserId(), monday))
                .map(this::toResource)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @GetMapping("/users/me/plan/{planId}")
    @Operation(summary = "Devuelve un plan por id, incluidas versiones ya reemplazadas")
    public ResponseEntity<WeeklyTrainingPlanResource> byId(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long planId) {

        return queryService.handle(new GetPlanByIdQuery(planId, principal.getUserId()))
                .map(this::toResource)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    @GetMapping("/users/me/plans")
    @Operation(summary = "Historial de planes del usuario, incluidas todas las versiones")
    public ResponseEntity<List<PlanSummaryResource>> history(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var resources = queryService.handle(new GetPlanHistoryQuery(principal.getUserId())).stream()
                .map(WeeklyTrainingPlanResourceFromEntityAssembler::toSummaryFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PostMapping("/workouts/{plannedWorkoutId}/skip")
    @Operation(summary = "Declara que un entrenamiento no se hara. La causa es obligatoria.")
    public ResponseEntity<Void> skip(@AuthenticationPrincipal UserDetailsImpl principal,
                                     @PathVariable Long plannedWorkoutId,
                                     @Valid @RequestBody SkipWorkoutResource resource) {

        commandService.handle(new SkipPlannedWorkoutCommand(
                principal.getUserId(), plannedWorkoutId, resource.reason()));
        return ResponseEntity.noContent().build();
    }

    /** Los nombres se resuelven contra el catalogo: planning guarda ids, no texto. */
    private WeeklyTrainingPlanResource toResource(WeeklyTrainingPlan plan) {
        Set<Long> exerciseIds = plan.workoutsView().stream()
                .flatMap(workout -> workout.exercisesView().stream())
                .map(exercise -> exercise.getExerciseId())
                .collect(Collectors.toSet());

        return WeeklyTrainingPlanResourceFromEntityAssembler.toResourceFromEntity(
                plan, externalCatalogService.fetchNames(exerciseIds));
    }
}
