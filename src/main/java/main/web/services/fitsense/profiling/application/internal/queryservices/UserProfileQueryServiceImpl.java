package main.web.services.fitsense.profiling.application.internal.queryservices;

import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import main.web.services.fitsense.profiling.domain.model.queries.GetUserProfileByUserIdQuery;
import main.web.services.fitsense.profiling.domain.services.UserProfileQueryService;
import main.web.services.fitsense.profiling.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserProfileQueryServiceImpl implements UserProfileQueryService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileQueryServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> handle(GetUserProfileByUserIdQuery query) {
        return userProfileRepository.findById(query.userId());
    }
}
