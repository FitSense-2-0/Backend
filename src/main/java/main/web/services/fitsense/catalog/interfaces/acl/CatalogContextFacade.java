package main.web.services.fitsense.catalog.interfaces.acl;

import main.web.services.fitsense.catalog.domain.model.aggregates.Exercise;
import main.web.services.fitsense.catalog.domain.model.queries.GetEligibleExercisesQuery;
import main.web.services.fitsense.catalog.domain.model.queries.GetExerciseByIdQuery;
import main.web.services.fitsense.catalog.domain.services.ExerciseQueryService;
import main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories.BodyPartRepository;
import main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories.EquipmentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unico punto de entrada al catalogo desde otros contextos. Lo consume planning
 * para armar el conjunto elegible y para validar que el generador no invento
 * ejercicios (validacion 1 de la seccion 19).
 */
@Service
public class CatalogContextFacade {

    private final ExerciseQueryService exerciseQueryService;
    private final BodyPartRepository bodyPartRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;

    public CatalogContextFacade(ExerciseQueryService exerciseQueryService,
                                BodyPartRepository bodyPartRepository,
                                EquipmentTypeRepository equipmentTypeRepository) {
        this.exerciseQueryService = exerciseQueryService;
        this.bodyPartRepository = bodyPartRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<EligibleExerciseView> fetchEligibleExercises(List<String> equipmentCodes,
                                                             boolean excludeGymOnly,
                                                             int maxDifficultyLevel,
                                                             List<Long> blockedExerciseIds) {
        var query = new GetEligibleExercisesQuery(
                equipmentCodes, excludeGymOnly, maxDifficultyLevel, blockedExerciseIds);

        var bodyParts = indexBodyParts();
        var equipment = indexEquipment();

        return exerciseQueryService.handle(query).stream()
                .map(exercise -> toView(exercise, bodyParts, equipment))
                .toList();
    }

    /** Nombre para mostrar de un conjunto de ids. Lo usa planning al exponer el plan. */
    @Transactional(readOnly = true)
    public Map<Long, String> fetchNames(Set<Long> exerciseIds) {
        if (exerciseIds == null || exerciseIds.isEmpty()) return Map.of();
        return exerciseIds.stream()
                .map(id -> exerciseQueryService.handle(new GetExerciseByIdQuery(id)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Exercise::getId, Exercise::displayName, (a, b) -> a));
    }

    private Map<Short, String> indexBodyParts() {
        var index = new HashMap<Short, String>();
        bodyPartRepository.findAll().forEach(bp -> index.put(bp.getId(), bp.getCode()));
        return index;
    }

    private Map<Short, String> indexEquipment() {
        var index = new HashMap<Short, String>();
        equipmentTypeRepository.findAll().forEach(eq -> index.put(eq.getId(), eq.getCode()));
        return index;
    }

    private static EligibleExerciseView toView(Exercise exercise,
                                               Map<Short, String> bodyParts,
                                               Map<Short, String> equipment) {
        return new EligibleExerciseView(
                exercise.getId(),
                exercise.displayName(),
                bodyParts.get(exercise.getBodyPartId()),
                equipment.get(exercise.getEquipmentId()),
                exercise.getTargetMuscle(),
                exercise.getDifficultyLevel(),
                exercise.getDefaultPrescription().name()
        );
    }
}
