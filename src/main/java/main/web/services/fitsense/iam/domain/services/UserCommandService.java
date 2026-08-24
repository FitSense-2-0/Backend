package main.web.services.fitsense.iam.domain.services;

import main.web.services.fitsense.iam.domain.model.aggregates.User;
import main.web.services.fitsense.iam.domain.model.commands.SignInCommand;
import main.web.services.fitsense.iam.domain.model.commands.SignUpCommand;
import main.web.services.fitsense.iam.domain.model.commands.WithdrawFromStudyCommand;

import java.util.Optional;

public interface UserCommandService {
    Optional<User> handle(SignUpCommand command);

    /** Devuelve el usuario autenticado y su token. */
    Optional<AuthenticatedUser> handle(SignInCommand command);

    Optional<User> handle(WithdrawFromStudyCommand command);

    record AuthenticatedUser(User user, String token) {}
}
