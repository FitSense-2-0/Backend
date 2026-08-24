package main.web.services.fitsense.profiling.domain.services;

import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import main.web.services.fitsense.profiling.domain.model.queries.GetUserProfileByUserIdQuery;

import java.util.Optional;

public interface UserProfileQueryService {
    Optional<UserProfile> handle(GetUserProfileByUserIdQuery query);
}
