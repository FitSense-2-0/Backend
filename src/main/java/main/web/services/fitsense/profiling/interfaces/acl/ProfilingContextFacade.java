package main.web.services.fitsense.profiling.interfaces.acl;

import main.web.services.fitsense.profiling.domain.model.aggregates.UserProfile;
import main.web.services.fitsense.profiling.domain.model.queries.GetUserProfileByUserIdQuery;
import main.web.services.fitsense.profiling.domain.services.UserProfileQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Unico punto de entrada al contexto de perfil. Lo consumen planning (para
 * armar el input_snapshot y el conjunto elegible) y adaptation (para los pisos
 * de dias y duracion).
 */
@Service
public class ProfilingContextFacade {

    private final UserProfileQueryService userProfileQueryService;

    public ProfilingContextFacade(UserProfileQueryService userProfileQueryService) {
        this.userProfileQueryService = userProfileQueryService;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileSnapshot> fetchProfile(Long userId) {
        return userProfileQueryService.handle(new GetUserProfileByUserIdQuery(userId))
                .map(ProfilingContextFacade::toSnapshot);
    }

    public boolean hasProfile(Long userId) {
        return fetchProfile(userId).isPresent();
    }

    private static ProfileSnapshot toSnapshot(UserProfile profile) {
        return new ProfileSnapshot(
                profile.getUserId(),
                profile.age(),
                profile.getBiologicalSex() == null ? null : profile.getBiologicalSex().name(),
                profile.getMeasurements().heightCm(),
                profile.getMeasurements().currentWeightKg(),
                profile.getMeasurements().targetWeightKg(),
                profile.getFitnessLevel().name(),
                profile.getGoal().goalType().name(),
                profile.getGoal().goalText(),
                profile.getTrainingLocation().name(),
                profile.getDaysPerWeek(),
                profile.availableDaysAsList(),
                profile.getSessionMinutes(),
                profile.maxSessionMinutesAllowed(),
                profile.maxDifficultyLevel(),
                profile.equipmentCodesAsList(),
                profile.blockedExerciseIdsAsList(),
                profile.getHealthNotes()
        );
    }
}
