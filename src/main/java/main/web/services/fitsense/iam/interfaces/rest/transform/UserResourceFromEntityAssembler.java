package main.web.services.fitsense.iam.interfaces.rest.transform;

import main.web.services.fitsense.iam.domain.model.aggregates.User;
import main.web.services.fitsense.iam.interfaces.rest.resources.UserResource;

public class UserResourceFromEntityAssembler {

    private UserResourceFromEntityAssembler() {}

    public static UserResource toResourceFromEntity(User entity) {
        var enrollment = entity.getEnrollment();
        return new UserResource(
                entity.getId(),
                entity.emailAddress(),
                entity.getName().firstName(),
                entity.getName().lastName(),
                entity.getTimezone().value(),
                entity.getStatus().name(),
                enrollment.studyParticipant(),
                enrollment.participantCode(),
                enrollment.enrolledAt(),
                enrollment.withdrawnAt(),
                entity.getLastLoginAt()
        );
    }
}
