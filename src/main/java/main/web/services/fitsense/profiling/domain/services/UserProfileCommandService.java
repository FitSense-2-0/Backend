package main.web.services.fitsense.profiling.domain.services;

import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import main.web.services.fitsense.profiling.domain.model.commands.CreateUserProfileCommand;
import main.web.services.fitsense.profiling.domain.model.commands.UpdateUserProfileCommand;

import java.util.Optional;

public interface UserProfileCommandService {
    Optional<UserProfile> handle(CreateUserProfileCommand command);
    Optional<UserProfile> handle(UpdateUserProfileCommand command);
}
