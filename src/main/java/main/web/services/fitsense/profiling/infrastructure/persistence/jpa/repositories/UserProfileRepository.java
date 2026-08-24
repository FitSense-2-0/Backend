package main.web.services.fitsense.profiling.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
