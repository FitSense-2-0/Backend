package main.web.services.fitsense.planning.infrastructure.generation.rules;

import main.web.services.fitsense.planning.domain.model.valueobjects.CandidateExercise;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutFocus;

import java.util.*;

/**
 * Seleccion de ejercicios de 20.3: al menos uno de cada body_part del enfoque,
 * luego se completa al azar sin repetir dentro de la misma sesion, evitando los
 * usados en los 7 dias anteriores mientras el conjunto elegible lo permita.
 */
class ExerciseSelector {

    private final Map<String, List<CandidateExercise>> byBodyPart = new LinkedHashMap<>();
    private final List<CandidateExercise> all;
    private final Set<Long> recentlyUsed;
    private final Random random;

    ExerciseSelector(List<CandidateExercise> available, int maxDifficulty,
                     Set<Long> recentlyUsed, Random random) {
        this.all = available.stream()
                .filter(candidate -> candidate.difficulty() <= maxDifficulty)
                .toList();
        this.recentlyUsed = recentlyUsed;
        this.random = random;
        all.forEach(candidate -> byBodyPart
                .computeIfAbsent(candidate.bodyPartCode(), key -> new ArrayList<>())
                .add(candidate));
    }

    List<CandidateExercise> pick(WorkoutFocus focus, int count) {
        var picked = new LinkedHashSet<CandidateExercise>();

        // Primero uno de cada parte corporal del enfoque, para que una sesion de
        // empuje no salga con cinco variantes de press de banca.
        for (var bodyPartCode : focus.bodyPartCodes()) {
            if (picked.size() >= count) break;
            pickOneFrom(pool(bodyPartCode), picked).ifPresent(picked::add);
        }

        // Luego se completa al azar dentro del enfoque.
        var focusPool = focus.bodyPartCodes().stream()
                .flatMap(code -> pool(code).stream())
                .distinct()
                .toList();
        while (picked.size() < count) {
            var candidate = pickOneFrom(focusPool, picked);
            if (candidate.isEmpty()) break;
            picked.add(candidate.get());
        }

        // Ultimo recurso: cualquier ejercicio elegible.
        //
        // El diseno no contempla este caso, pero los datos lo exigen: en el
        // dataset solo hay 2 ejercicios de hombro con peso corporal, asi que un
        // perfil HOME + BEGINNER con enfoque PUSH se queda corto. Un plan menos
        // especifico es mejor que una sesion de un solo ejercicio, que ademas
        // fallaria la validacion 5.
        while (picked.size() < count) {
            var candidate = pickOneFrom(all, picked);
            if (candidate.isEmpty()) break;
            picked.add(candidate.get());
        }

        return List.copyOf(picked);
    }

    private List<CandidateExercise> pool(String bodyPartCode) {
        return byBodyPart.getOrDefault(bodyPartCode, List.of());
    }

    /**
     * Prefiere lo no usado en los ultimos 7 dias, pero cede antes que devolver
     * vacio: la variedad es deseable, tener plan es obligatorio.
     */
    private Optional<CandidateExercise> pickOneFrom(List<CandidateExercise> pool,
                                                    Set<CandidateExercise> alreadyPicked) {
        var fresh = pool.stream()
                .filter(candidate -> !alreadyPicked.contains(candidate))
                .filter(candidate -> !recentlyUsed.contains(candidate.exerciseId()))
                .toList();

        if (!fresh.isEmpty()) return Optional.of(fresh.get(random.nextInt(fresh.size())));

        var reusable = pool.stream()
                .filter(candidate -> !alreadyPicked.contains(candidate))
                .toList();

        if (reusable.isEmpty()) return Optional.empty();
        return Optional.of(reusable.get(random.nextInt(reusable.size())));
    }
}
