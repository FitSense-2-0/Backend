package main.web.services.fitsense.shared.interfaces.rest.resources;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResource(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {
    public static ApiErrorResource of(int status, String error, String message, List<String> details) {
        return new ApiErrorResource(OffsetDateTime.now(), status, error, message, details);
    }
}
