package main.web.services.fitsense.profiling.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import main.web.services.fitsense.profiling.domain.exceptions.UserProfileNotFoundException;
import main.web.services.fitsense.profiling.domain.model.queries.GetUserProfileByUserIdQuery;
import main.web.services.fitsense.profiling.domain.services.UserProfileCommandService;
import main.web.services.fitsense.profiling.domain.services.UserProfileQueryService;
import main.web.services.fitsense.profiling.interfaces.rest.resources.CreateUserProfileResource;
import main.web.services.fitsense.profiling.interfaces.rest.resources.UpdateUserProfileResource;
import main.web.services.fitsense.profiling.interfaces.rest.resources.UserProfileResource;
import main.web.services.fitsense.profiling.interfaces.rest.transform.CreateUserProfileCommandFromResourceAssembler;
import main.web.services.fitsense.profiling.interfaces.rest.transform.UpdateUserProfileCommandFromResourceAssembler;
import main.web.services.fitsense.profiling.interfaces.rest.transform.UserProfileResourceFromEntityAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * El user_id sale siempre del JWT. El cliente nunca lo decide (seccion 21).
 */
@RestController
@RequestMapping(value = "/api/v1/users/me/profile", produces = "application/json")
@Tag(name = "Perfil", description = "Perfil fisico, objetivo y disponibilidad")
public class UserProfileController {

    private final UserProfileCommandService userProfileCommandService;
    private final UserProfileQueryService userProfileQueryService;

    public UserProfileController(UserProfileCommandService userProfileCommandService,
                                 UserProfileQueryService userProfileQueryService) {
        this.userProfileCommandService = userProfileCommandService;
        this.userProfileQueryService = userProfileQueryService;
    }

    @GetMapping
    @Operation(summary = "Devuelve el perfil del usuario autenticado")
    public ResponseEntity<UserProfileResource> get(@AuthenticationPrincipal UserDetailsImpl principal) {
        return userProfileQueryService.handle(new GetUserProfileByUserIdQuery(principal.getUserId()))
                .map(UserProfileResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new UserProfileNotFoundException(principal.getUserId()));
    }

    @PostMapping
    @Operation(summary = "Crea el perfil. Solo una vez por usuario.")
    public ResponseEntity<UserProfileResource> create(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody CreateUserProfileResource resource) {

        var command = CreateUserProfileCommandFromResourceAssembler
                .toCommandFromResource(principal.getUserId(), resource);

        return userProfileCommandService.handle(command)
                .map(UserProfileResourceFromEntityAssembler::toResourceFromEntity)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @PutMapping
    @Operation(summary = "Actualiza el perfil. Los cambios se aplican en la generacion del lunes siguiente.")
    public ResponseEntity<UserProfileResource> update(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody UpdateUserProfileResource resource) {

        var command = UpdateUserProfileCommandFromResourceAssembler
                .toCommandFromResource(principal.getUserId(), resource);

        return userProfileCommandService.handle(command)
                .map(UserProfileResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new UserProfileNotFoundException(principal.getUserId()));
    }
}
