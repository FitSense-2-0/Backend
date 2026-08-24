package main.web.services.fitsense.iam.interfaces.rest.resources;

public record AuthenticatedUserResource(Long userId, String email, String fullName, String token) {}
