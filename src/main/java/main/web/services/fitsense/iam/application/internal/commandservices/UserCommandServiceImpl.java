package main.web.services.fitsense.iam.application.internal.commandservices;

import main.web.services.fitsense.iam.application.internal.outboundservices.hashing.HashingService;
import main.web.services.fitsense.iam.application.internal.outboundservices.tokens.TokenService;
import main.web.services.fitsense.iam.domain.exceptions.EmailAlreadyRegisteredException;
import main.web.services.fitsense.iam.domain.exceptions.InvalidCredentialsException;
import main.web.services.fitsense.iam.domain.model.aggregates.User;
import main.web.services.fitsense.iam.domain.model.commands.SignInCommand;
import main.web.services.fitsense.iam.domain.model.commands.SignUpCommand;
import main.web.services.fitsense.iam.domain.model.commands.WithdrawFromStudyCommand;
import main.web.services.fitsense.iam.domain.model.valueobjects.EmailAddress;
import main.web.services.fitsense.iam.domain.services.UserCommandService;
import main.web.services.fitsense.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final String consentVersion;

    public UserCommandServiceImpl(UserRepository userRepository,
                                  HashingService hashingService,
                                  TokenService tokenService,
                                  @Value("${fitsense.study.consent-version:MVP-1.0}") String consentVersion) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.consentVersion = consentVersion;
    }

    @Override
    @Transactional
    public Optional<User> handle(SignUpCommand command) {
        var email = new EmailAddress(command.email());
        if (userRepository.existsByEmail(email))
            throw new EmailAlreadyRegisteredException(email.address());

        var participantCode = command.acceptsStudyParticipation() ? nextParticipantCode() : null;
        var user = new User(command, hashingService.encode(command.rawPassword()),
                consentVersion, participantCode);

        return Optional.of(userRepository.save(user));
    }

    @Override
    @Transactional
    public Optional<AuthenticatedUser> handle(SignInCommand command) {
        var user = userRepository.findByEmail(new EmailAddress(command.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!hashingService.matches(command.rawPassword(), user.getPasswordHash()))
            throw new InvalidCredentialsException();
        if (!user.canSignIn())
            throw new InvalidCredentialsException();

        user.registerLogin();
        userRepository.save(user);

        var token = tokenService.generateToken(user.getId(), user.emailAddress());
        return Optional.of(new AuthenticatedUser(user, token));
    }

    @Override
    @Transactional
    public Optional<User> handle(WithdrawFromStudyCommand command) {
        var user = userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", command.userId()));
        user.withdrawFromStudy();
        return Optional.of(userRepository.save(user));
    }

    /**
     * Codigo anonimizado del analisis (P-001, P-002...). El contador puede
     * colisionar bajo concurrencia; el UNIQUE de la base es la garantia real.
     * Con el volumen del estudio (decenas de participantes) es suficiente.
     */
    private String nextParticipantCode() {
        return "P-%03d".formatted(userRepository.countStudyParticipants() + 1);
    }
}
