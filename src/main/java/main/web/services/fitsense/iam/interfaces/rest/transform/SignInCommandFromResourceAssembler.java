package main.web.services.fitsense.iam.interfaces.rest.transform;

import main.web.services.fitsense.iam.domain.model.commands.SignInCommand;
import main.web.services.fitsense.iam.interfaces.rest.resources.SignInResource;

public class SignInCommandFromResourceAssembler {

    private SignInCommandFromResourceAssembler() {}

    public static SignInCommand toCommandFromResource(SignInResource resource) {
        return new SignInCommand(resource.email(), resource.password());
    }
}
