package main.web.services.fitsense.iam.infrastructure.hashing.bcrypt;

import main.web.services.fitsense.iam.application.internal.outboundservices.hashing.HashingService;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Marca el adaptador BCrypt para poder inyectarlo tambien como PasswordEncoder. */
public interface BCryptHashingService extends HashingService, PasswordEncoder {
}
