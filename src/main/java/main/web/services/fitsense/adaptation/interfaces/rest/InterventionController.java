package main.web.services.fitsense.adaptation.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import main.web.services.fitsense.adaptation.domain.model.queries.GetInterventionHistoryQuery;
import main.web.services.fitsense.adaptation.domain.services.UserInterventionQueryService;
import main.web.services.fitsense.adaptation.interfaces.rest.resources.InterventionResource;
import main.web.services.fitsense.adaptation.interfaces.rest.transform.InterventionResourceFromEntityAssembler;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Solo lectura. El ajuste no se pide: lo decide la regla al cerrar la semana.
 * Un endpoint para solicitarlo dejaria al participante eligiendo la variable
 * independiente del experimento.
 */
@RestController
@RequestMapping(value = "/api/v1/users/me/interventions", produces = "application/json")
@Tag(name = "Adaptacion", description = "Ajustes aplicados al plan y su resultado")
public class InterventionController {

    private final UserInterventionQueryService queryService;

    public InterventionController(UserInterventionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Historial de ajustes del usuario autenticado")
    public ResponseEntity<List<InterventionResource>> history(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var resources = queryService.handle(new GetInterventionHistoryQuery(principal.getUserId()))
                .stream()
                .map(InterventionResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
