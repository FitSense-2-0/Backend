package main.web.services.fitsense.iam.infrastructure.tokens.jwt.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import main.web.services.fitsense.iam.infrastructure.tokens.jwt.BearerTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
public class TokenServiceImpl implements BearerTokenService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public TokenServiceImpl(@Value("${authorization.jwt.secret}") String secret,
                            @Value("${authorization.jwt.expiration-days:7}") int expirationDays) {
        var keyBytes = decodeSecret(secret);
        if (keyBytes.length < 32)
            throw new IllegalStateException(
                    "authorization.jwt.secret debe tener al menos 32 bytes para HS256.");
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationDays * 24L * 60 * 60 * 1000;
    }

    private static byte[] decodeSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (Exception ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public String generateToken(Long userId, String email) {
        var now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    @Override
    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rechazado: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getBearerTokenFrom(HttpServletRequest request) {
        var header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null;
        return header.substring(BEARER_PREFIX.length());
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
    }
}
