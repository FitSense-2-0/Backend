package main.web.services.fitsense.iam.application.internal.queryservices;

import main.web.services.fitsense.iam.domain.model.aggregates.User;
import main.web.services.fitsense.iam.domain.model.queries.GetUserByEmailQuery;
import main.web.services.fitsense.iam.domain.model.queries.GetUserByIdQuery;
import main.web.services.fitsense.iam.domain.model.valueobjects.EmailAddress;
import main.web.services.fitsense.iam.domain.services.UserQueryService;
import main.web.services.fitsense.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;

    public UserQueryServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> handle(GetUserByIdQuery query) {
        return userRepository.findById(query.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> handle(GetUserByEmailQuery query) {
        return userRepository.findByEmail(new EmailAddress(query.email()));
    }
}
