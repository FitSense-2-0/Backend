package main.web.services.fitsense.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import main.web.services.fitsense.iam.domain.model.commands.WithdrawFromStudyCommand;
import main.web.services.fitsense.iam.domain.model.queries.GetUserByIdQuery;
import main.web.services.fitsense.iam.domain.services.UserCommandService;
import main.web.services.fitsense.iam.domain.services.UserQueryService;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import main.web.services.fitsense.iam.interfaces.rest.resources.UserResource;
import main.web.services.fitsense.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/users", produces = "application/json")
@Tag(name = "Usuarios", description = "Cuenta del usuario autenticado")
public class UsersController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    public UsersController(UserQueryService userQueryService, UserCommandService userCommandService) {
        this.userQueryService = userQueryService;
        this.userCommandService = userCommandService;
    }

    @GetMapping("/me")
    @Operation(summary = "Devuelve la cuenta del usuario del JWT")
    public ResponseEntity<UserResource> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        return userQueryService.handle(new GetUserByIdQuery(principal.getUserId()))
                .map(UserResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", principal.getUserId()));
    }

    @PostMapping("/me/withdraw")
    @Operation(summary = "Retiro voluntario del estudio. No borra los datos ya recolectados.")
    public ResponseEntity<UserResource> withdraw(@AuthenticationPrincipal UserDetailsImpl principal) {
        return userCommandService.handle(new WithdrawFromStudyCommand(principal.getUserId()))
                .map(UserResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", principal.getUserId()));
    }
}
