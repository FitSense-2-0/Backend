package main.web.services.fitsense.profiling.application.internal.commandservices;

import main.web.services.fitsense.profiling.application.internal.outboundservices.acl.ExternalIamService;
import main.web.services.fitsense.profiling.domain.exceptions.UserProfileAlreadyExistsException;
import main.web.services.fitsense.profiling.domain.exceptions.UserProfileNotFoundException;
import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import main.web.services.fitsense.profiling.domain.model.commands.CreateUserProfileCommand;
import main.web.services.fitsense.profiling.domain.model.commands.UpdateUserProfileCommand;
import main.web.services.fitsense.profiling.domain.services.UserProfileCommandService;
import main.web.services.fitsense.profiling.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import main.web.services.fitsense.shared.domain.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserProfileCommandServiceImpl implements UserProfileCommandService {

    private final UserProfileRepository userProfileRepository;
    private final ExternalIamService externalIamService;

    public UserProfileCommandServiceImpl(UserProfileRepository userProfileRepository,
                                         ExternalIamService externalIamService) {
        this.userProfileRepository = userProfileRepository;
        this.externalIamService = externalIamService;
    }

    @Override
    @Transactional
    public Optional<UserProfile> handle(CreateUserProfileCommand command) {
        if (!externalIamService.userExists(command.userId()))
            throw new ResourceNotFoundException("Usuario", command.userId());
        if (userProfileRepository.existsById(command.userId()))
            throw new UserProfileAlreadyExistsException(command.userId());

        var profile = new UserProfile(command.userId(), command);
        return Optional.of(userProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public Optional<UserProfile> handle(UpdateUserProfileCommand command) {
        var profile = userProfileRepository.findById(command.userId())
                .orElseThrow(() -> new UserProfileNotFoundException(command.userId()));

        profile.update(command);
        return Optional.of(userProfileRepository.save(profile));
    }
}
