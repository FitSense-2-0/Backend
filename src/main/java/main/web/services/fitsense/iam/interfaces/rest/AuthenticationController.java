package main.web.services.fitsense.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import main.web.services.fitsense.iam.domain.services.UserCommandService;
import main.web.services.fitsense.iam.interfaces.rest.resources.AuthenticatedUserResource;
import main.web.services.fitsense.iam.interfaces.rest.resources.SignInResource;
import main.web.services.fitsense.iam.interfaces.rest.resources.SignUpResource;
import main.web.services.fitsense.iam.interfaces.rest.resources.UserResource;
import main.web.services.fitsense.iam.interfaces.rest.transform.AuthenticatedUserResourceFromEntityAssembler;
import main.web.services.fitsense.iam.interfaces.rest.transform.SignInCommandFromResourceAssembler;
import main.web.services.fitsense.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import main.web.services.fitsense.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirements
@RequestMapping(value = "/api/v1/auth", produces = "application/json")
@Tag(name = "Autenticacion", description = "Registro e inicio de sesion")
public class AuthenticationController {

    private final UserCommandService userCommandService;

    public AuthenticationController(UserCommandService userCommandService) {
        this.userCommandService = userCommandService;
    }

    @PostMapping("/register")
    @Operation(summary = "Registra una cuenta y, si corresponde, la inscribe en el estudio")
    public ResponseEntity<UserResource> register(@Valid @RequestBody SignUpResource resource) {
        var command = SignUpCommandFromResourceAssembler.toCommandFromResource(resource);
        return userCommandService.handle(command)
                .map(UserResourceFromEntityAssembler::toResourceFromEntity)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesion y devuelve el token JWT")
    public ResponseEntity<AuthenticatedUserResource> login(@Valid @RequestBody SignInResource resource) {
        var command = SignInCommandFromResourceAssembler.toCommandFromResource(resource);
        return userCommandService.handle(command)
                .map(auth -> AuthenticatedUserResourceFromEntityAssembler
                        .toResourceFromEntity(auth.user(), auth.token()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
