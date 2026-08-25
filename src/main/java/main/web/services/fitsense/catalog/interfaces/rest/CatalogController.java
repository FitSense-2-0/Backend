package main.web.services.fitsense.catalog.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import main.web.services.fitsense.catalog.domain.exceptions.ExerciseNotFoundException;
import main.web.services.fitsense.catalog.domain.model.queries.*;
import main.web.services.fitsense.catalog.domain.services.CatalogTaxonomyQueryService;
import main.web.services.fitsense.catalog.domain.services.ExerciseQueryService;
import main.web.services.fitsense.catalog.interfaces.rest.resources.BodyPartResource;
import main.web.services.fitsense.catalog.interfaces.rest.resources.EquipmentTypeResource;
import main.web.services.fitsense.catalog.interfaces.rest.resources.ExerciseDetailResource;
import main.web.services.fitsense.catalog.interfaces.rest.resources.ExerciseSummaryResource;
import main.web.services.fitsense.catalog.interfaces.rest.transform.BodyPartResourceFromEntityAssembler;
import main.web.services.fitsense.catalog.interfaces.rest.transform.EquipmentTypeResourceFromEntityAssembler;
import main.web.services.fitsense.catalog.interfaces.rest.transform.ExerciseResourceFromEntityAssembler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalogo de solo lectura. No hay POST, PUT ni DELETE a proposito: los datos se
 * cargan por migracion. Si el catalogo fuese editable en caliente, un plan ya
 * generado podria apuntar a un ejercicio que cambio de significado y la
 * adherencia dejaria de ser comparable entre semanas.
 */
@RestController
@RequestMapping(value = "/api/v1/catalog", produces = "application/json")
@Tag(name = "Catalogo", description = "Ejercicios, grupos musculares y equipamiento")
public class CatalogController {

    private final ExerciseQueryService exerciseQueryService;
    private final CatalogTaxonomyQueryService catalogTaxonomyQueryService;

    public CatalogController(ExerciseQueryService exerciseQueryService,
                             CatalogTaxonomyQueryService catalogTaxonomyQueryService) {
        this.exerciseQueryService = exerciseQueryService;
        this.catalogTaxonomyQueryService = catalogTaxonomyQueryService;
    }

    @GetMapping("/body-parts")
    @Operation(summary = "Lista los grupos musculares")
    public ResponseEntity<List<BodyPartResource>> bodyParts() {
        var resources = catalogTaxonomyQueryService.handle(new GetBodyPartsQuery()).stream()
                .map(BodyPartResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/equipment-types")
    @Operation(summary = "Lista el equipamiento. requiresGym marca lo que no aplica en casa.")
    public ResponseEntity<List<EquipmentTypeResource>> equipmentTypes() {
        var resources = catalogTaxonomyQueryService.handle(new GetEquipmentTypesQuery()).stream()
                .map(EquipmentTypeResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/exercises")
    @Operation(summary = "Busca en el catalogo activo. Los filtros nulos no restringen.")
    public ResponseEntity<List<ExerciseSummaryResource>> exercises(
            @Parameter(description = "Codigo del grupo muscular, por ejemplo 'chest'")
            @RequestParam(required = false) String bodyPart,
            @Parameter(description = "Codigo del equipamiento, por ejemplo 'dumbbell'")
            @RequestParam(required = false) String equipment,
            @Parameter(description = "Dificultad maxima: 1 principiante, 3 avanzado")
            @RequestParam(required = false) Short maxDifficulty,
            @Parameter(description = "Texto contenido en el nombre en espanol")
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {

        var query = GetActiveExercisesQuery.of(bodyPart, equipment, maxDifficulty, search, limit);
        var resources = exerciseQueryService.handle(query).stream()
                .map(ExerciseResourceFromEntityAssembler::toSummaryFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/exercises/{exerciseId}")
    @Operation(summary = "Devuelve un ejercicio con instrucciones y media")
    public ResponseEntity<ExerciseDetailResource> exercise(@PathVariable Long exerciseId) {
        return exerciseQueryService.handle(new GetExerciseByIdQuery(exerciseId))
                .map(ExerciseResourceFromEntityAssembler::toDetailFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ExerciseNotFoundException(exerciseId));
    }
}
