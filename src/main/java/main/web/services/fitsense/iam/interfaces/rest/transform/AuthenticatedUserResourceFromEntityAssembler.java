package main.web.services.fitsense.iam.interfaces.rest.transform;

import main.web.services.fitsense.iam.domain.model.aggregates.User;
import main.web.services.fitsense.iam.interfaces.rest.resources.AuthenticatedUserResource;

public class AuthenticatedUserResourceFromEntityAssembler {

    private AuthenticatedUserResourceFromEntityAssembler() {}

    public static AuthenticatedUserResource toResourceFromEntity(User entity, String token) {
        return new AuthenticatedUserResource(
                entity.getId(),
                entity.emailAddress(),
                entity.getName().fullName(),
                token
        );
    }
}
