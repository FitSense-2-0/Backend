package main.web.services.fitsense.execution.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import main.web.services.fitsense.execution.domain.exceptions.SessionNotFoundException;
import main.web.services.fitsense.execution.domain.model.commands.*;
import main.web.services.fitsense.execution.domain.model.queries.GetCurrentSessionQuery;
import main.web.services.fitsense.execution.domain.model.queries.GetSessionByIdQuery;
import main.web.services.fitsense.execution.domain.services.WorkoutSessionCommandService;
import main.web.services.fitsense.execution.domain.services.WorkoutSessionQueryService;
import main.web.services.fitsense.execution.interfaces.rest.resources.FinishSessionResource;
import main.web.services.fitsense.execution.interfaces.rest.resources.RecordExerciseResource;
import main.web.services.fitsense.execution.interfaces.rest.resources.ReportWorkoutResource;
import main.web.services.fitsense.execution.interfaces.rest.resources.WorkoutSessionResource;
import main.web.services.fitsense.execution.interfaces.rest.transform.ReportWorkoutCommandFromResourceAssembler;
import main.web.services.fitsense.execution.interfaces.rest.transform.WorkoutSessionResourceFromEntityAssembler;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * El ciclo de una sesion: abrir, registrar ejercicio por ejercicio, cerrar.
 * Mas el atajo del reporte retroactivo para lo que ya paso.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = "application/json")
@Tag(name = "Ejecucion", description = "Sesiones de entrenamiento realizadas")
public class WorkoutSessionController {

    private final WorkoutSessionCommandService commandService;
    private final WorkoutSessionQueryService queryService;

    public WorkoutSessionController(WorkoutSessionCommandService commandService,
                                    WorkoutSessionQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/workouts/{plannedWorkoutId}/start")
    @Operation(summary = "Empieza el entrenamiento. Falla si ya vencio o si hay otra sesion abierta.")
    public ResponseEntity<WorkoutSessionResource> start(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long plannedWorkoutId) {

        return commandService.handle(new StartWorkoutSessionCommand(principal.getUserId(), plannedWorkoutId))
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @GetMapping("/sessions/current")
    @Operation(summary = "Devuelve la sesion en curso, si la hay")
    public ResponseEntity<WorkoutSessionResource> current(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        return queryService.handle(new GetCurrentSessionQuery(principal.getUserId()))
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Devuelve una sesion del usuario autenticado")
    public ResponseEntity<WorkoutSessionResource> byId(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long sessionId) {

        return queryService.handle(new GetSessionByIdQuery(sessionId, principal.getUserId()))
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @PutMapping("/sessions/{sessionId}/exercises/{plannedExerciseId}")
    @Operation(summary = "Registra el resultado de un ejercicio. Reenviarlo sobrescribe, no acumula.")
    public ResponseEntity<WorkoutSessionResource> recordExercise(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long sessionId,
            @PathVariable Long plannedExerciseId,
            @Valid @RequestBody RecordExerciseResource resource) {

        // El ejercicio va en la ruta, no en el cuerpo: es lo que identifica el
        // recurso que se esta reemplazando, y hace explicito que reenviar el
        // mismo PUT sobrescribe en vez de acumular.
        var command = new RecordSessionExerciseCommand(principal.getUserId(), sessionId,
                plannedExerciseId, resource.actualSets(), resource.actualRepsTotal(),
                resource.actualDurationSeconds(), resource.actualLoadKg(), resource.skipReason());

        return commandService.handle(command)
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @Operation(summary = "Cierra la sesion y calcula el porcentaje contra lo indicado")
    public ResponseEntity<WorkoutSessionResource> finish(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody FinishSessionResource resource) {

        var command = new FinishWorkoutSessionCommand(principal.getUserId(), sessionId,
                resource.sessionRpe(), resource.satisfaction(), resource.activeMinutes());

        return commandService.handle(command)
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/abandon")
    @Operation(summary = "Abandona la sesion. No cuenta como intento y el entrenamiento sigue exigible.")
    public ResponseEntity<WorkoutSessionResource> abandon(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long sessionId) {

        return commandService.handle(new AbandonWorkoutSessionCommand(principal.getUserId(), sessionId))
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @PostMapping("/workouts/{plannedWorkoutId}/report")
    @Operation(summary = "Registra de una vez un entrenamiento ya hecho, incluso vencido")
    public ResponseEntity<WorkoutSessionResource> report(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable Long plannedWorkoutId,
            @Valid @RequestBody ReportWorkoutResource resource) {

        var command = ReportWorkoutCommandFromResourceAssembler
                .toCommandFromResource(principal.getUserId(), plannedWorkoutId, resource);

        return commandService.handle(command)
                .map(WorkoutSessionResourceFromEntityAssembler::toResourceFromEntity)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }
}
