package main.web.services.fitsense.iam.domain.services;

import main.web.services.fitsense.iam.domain.model.aggregates.User;
import main.web.services.fitsense.iam.domain.model.queries.GetUserByEmailQuery;
import main.web.services.fitsense.iam.domain.model.queries.GetUserByIdQuery;

import java.util.Optional;

public interface UserQueryService {
    Optional<User> handle(GetUserByIdQuery query);
    Optional<User> handle(GetUserByEmailQuery query);
}
