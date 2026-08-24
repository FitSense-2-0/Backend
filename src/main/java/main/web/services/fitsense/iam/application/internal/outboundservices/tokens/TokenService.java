package main.web.services.fitsense.iam.application.internal.outboundservices.tokens;

public interface TokenService {
    String generateToken(Long userId, String email);
    Long extractUserId(String token);
    boolean validateToken(String token);
    String getBearerTokenFrom(jakarta.servlet.http.HttpServletRequest request);
}
