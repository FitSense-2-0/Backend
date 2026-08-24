package main.web.services.fitsense.iam.domain.model.commands;

public record SignUpCommand(
        String email,
        String rawPassword,
        String firstName,
        String lastName,
        String timezone,
        boolean acceptsStudyParticipation
) {}
