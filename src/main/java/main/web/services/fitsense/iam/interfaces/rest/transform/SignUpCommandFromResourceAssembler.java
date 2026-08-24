package main.web.services.fitsense.iam.interfaces.rest.transform;

import main.web.services.fitsense.iam.domain.model.commands.SignUpCommand;
import main.web.services.fitsense.iam.domain.model.valueobjects.UserTimezone;
import main.web.services.fitsense.iam.interfaces.rest.resources.SignUpResource;

public class SignUpCommandFromResourceAssembler {

    private SignUpCommandFromResourceAssembler() {}

    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        var zone = (resource.timezone() == null || resource.timezone().isBlank())
                ? UserTimezone.DEFAULT
                : resource.timezone();
        return new SignUpCommand(
                resource.email(),
                resource.password(),
                resource.firstName(),
                resource.lastName(),
                zone,
                resource.acceptsStudyParticipation()
        );
    }
}
