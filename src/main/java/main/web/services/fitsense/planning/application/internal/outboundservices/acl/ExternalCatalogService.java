package main.web.services.fitsense.planning.application.internal.outboundservices.acl;

import main.web.services.fitsense.catalog.interfaces.acl.CatalogContextFacade;
import main.web.services.fitsense.planning.domain.model.valueobjects.CandidateExercise;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanningProfile;
import main.web.services.fitsense.planning.domain.model.valueobjects.PrescriptionType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Capa anticorrupcion hacia el catalogo: el conjunto elegible de 20.3. */
@Service
public class ExternalCatalogService {

    private final CatalogContextFacade catalogContextFacade;

    public ExternalCatalogService(CatalogContextFacade catalogContextFacade) {
        this.catalogContextFacade = catalogContextFacade;
    }

    public List<CandidateExercise> fetchEligibleFor(PlanningProfile profile, int maxDifficultyLevel) {
        return catalogContextFacade.fetchEligibleExercises(
                        profile.equipmentCodes(),
                        profile.excludesGymOnlyEquipment(),
                        maxDifficultyLevel,
                        profile.blockedExerciseIds())
                .stream()
                .map(view -> new CandidateExercise(
                        view.exerciseId(),
                        view.nameEs(),
                        view.bodyPartCode(),
                        view.equipmentCode(),
                        view.difficultyLevel(),
                        view.isDurationBased() ? PrescriptionType.DURATION : PrescriptionType.SETS_REPS))
                .toList();
    }

    public Map<Long, String> fetchNames(Set<Long> exerciseIds) {
        return catalogContextFacade.fetchNames(exerciseIds);
    }
}
