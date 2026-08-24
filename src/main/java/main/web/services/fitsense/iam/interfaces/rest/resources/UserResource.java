package main.web.services.fitsense.iam.interfaces.rest.resources;

import java.time.OffsetDateTime;

public record UserResource(
        Long userId,
        String email,
        String firstName,
        String lastName,
        String timezone,
        String status,
        boolean studyParticipant,
        String participantCode,
        OffsetDateTime enrolledAt,
        OffsetDateTime withdrawnAt,
        OffsetDateTime lastLoginAt
) {}
