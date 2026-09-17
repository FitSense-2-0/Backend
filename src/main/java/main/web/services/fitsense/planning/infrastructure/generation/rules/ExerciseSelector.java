package main.web.services.fitsense.planning.infrastructure.generation.rules;

import main.web.services.fitsense.planning.domain.model.valueobjects.CandidateExercise;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutFocus;

import java.util.*;

/**
 * Seleccion de ejercicios de 20.3: al menos uno de cada body_part del enfoque,
 * y el resto repartido por turnos entre esos mismos grupos.
 * <p>
 * DESVIACION DOCUMENTADA: el 20.3 dice "se completa al azar". Ya no. El azar
 * dentro del enfoque concentraba en el grupo mas poblado del catalogo —waist
 * tiene 84 ejercicios de peso corporal y lower legs 13— y eso hacia que
 * LOWER_BODY cayera sistematicamente por la regla de la mitad de V15. El
 * respaldo determinista incumplia las mismas validaciones que el generador al
 * que respalda, asi que dejaba al participante sin plan.
 */
class ExerciseSelector {

    private final Map<String, List<CandidateExercise>> byBodyPart = new LinkedHashMap<>();
    private final List<CandidateExercise> all;
    private final Set<Long> recentlyUsed;
    private final Random random;

    /**
     * Ejercicios prohibidos en la proxima sesion: los del dia anterior cuando
     * los dias son consecutivos (V18). A diferencia de recentlyUsed, que es solo
     * una preferencia, esto es un bloqueo duro.
     */
    private Set<Long> blocked = Set.of();

    void block(Set<Long> exerciseIds) {
        this.blocked = exerciseIds == null ? Set.of() : Set.copyOf(exerciseIds);
    }

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
        var grupos = List.copyOf(focus.bodyPartCodes());

        // V15 rechaza cuando un grupo supera la mitad de la sesion (division
        // entera), y solo si el enfoque abarca 3 o mas grupos y la sesion tiene
        // 4 o mas ejercicios. Fuera de ese caso no hay tope que respetar.
        int tope = (grupos.size() >= 3 && count >= 4) ? count / 2 : count;

        var porGrupo = new LinkedHashMap<String, Integer>();
        grupos.forEach(code -> porGrupo.put(code, 0));

        // Reparto por turnos. Con n ejercicios sobre k grupos ninguno pasa de
        // ceil(n/k), que para todos los enfoques de 3+ grupos cae dentro del
        // tope: LOWER_BODY con 5 sale 2+2+1, que es la unica reparticion legal.
        boolean progreso = true;
        while (picked.size() < count && progreso) {
            progreso = false;
            for (var code : grupos) {
                if (picked.size() >= count) break;
                if (porGrupo.get(code) >= tope) continue;
                var candidate = pickOneFrom(pool(code, focus), picked);
                if (candidate.isEmpty()) continue;
                picked.add(candidate.get());
                porGrupo.merge(code, 1, Integer::sum);
                progreso = true;
            }
        }

        // Solo FULL_BODY admite grupos ajenos: V15 lo exime explicitamente. Para
        // el resto, completar con otro grupo produce el rechazo "incluye
        // ejercicios de [...]", asi que es preferible una sesion mas corta. La
        // regla de la mitad tampoco aplica por debajo de 4 ejercicios, de modo
        // que acortar nunca empeora la situacion.
        if (focus == WorkoutFocus.FULL_BODY) {
            completarCon(all, picked, count);
        }

        // V5 exige un minimo de 2 ejercicios. Si el enfoque no da ni para eso
        // —un PUSH en casa sin equipo, donde solo hay 2 ejercicios de hombro—
        // es preferible un ejercicio ajeno a una sesion que no existe.
        if (picked.size() < 2) completarCon(all, picked, 2);

        return List.copyOf(picked);
    }

    /**
     * Un ejercicio mas para una sesion ya armada, respetando lo que V15 exige:
     * grupos del enfoque y ninguno por encima de la mitad. Lo usa el motor de
     * reglas para llegar a la duracion minima (V20) con trabajo, no con pausas.
     */
    Optional<CandidateExercise> pickAdditional(WorkoutFocus focus, List<CandidateExercise> current) {
        var picked = new LinkedHashSet<>(current);
        var grupos = List.copyOf(focus.bodyPartCodes());
        int total = current.size() + 1;
        int tope = (grupos.size() >= 3 && total >= 4) ? total / 2 : total;

        var porGrupo = new HashMap<String, Long>();
        current.forEach(candidate -> porGrupo.merge(candidate.bodyPartCode(), 1L, Long::sum));

        var ordenados = grupos.stream()
                .sorted(Comparator.comparingLong(code -> porGrupo.getOrDefault(code, 0L)))
                .toList();
        for (var code : ordenados) {
            if (porGrupo.getOrDefault(code, 0L) + 1 > tope) continue;
            var candidate = pickOneFrom(pool(code, focus), picked);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    private void completarCon(List<CandidateExercise> pool,
                              Set<CandidateExercise> picked, int count) {
        while (picked.size() < count) {
            var candidate = pickOneFrom(pool, picked);
            if (candidate.isEmpty()) break;
            picked.add(candidate.get());
        }
    }

    private List<CandidateExercise> pool(String bodyPartCode, WorkoutFocus focus) {
        return byBodyPart.getOrDefault(bodyPartCode, List.of()).stream()
                .filter(candidate -> allowedIn(focus, candidate))
                .toList();
    }

    /**
     * V22: en "upper arms", PUSH solo admite triceps y PULL solo biceps. Con
     * target_muscle desconocido se admite: no se rechaza por falta de dato.
     */
    static boolean allowedIn(WorkoutFocus focus, CandidateExercise candidate) {
        if (!"upper arms".equals(candidate.bodyPartCode()) || candidate.targetMuscle() == null) return true;
        String muscle = candidate.targetMuscle().toLowerCase(java.util.Locale.ROOT);
        if (focus == WorkoutFocus.PUSH) return !muscle.contains("biceps");
        if (focus == WorkoutFocus.PULL) return !muscle.contains("triceps");
        return true;
    }

    /**
     * Prefiere lo no usado en los ultimos 7 dias, pero cede antes que devolver
     * vacio: la variedad es deseable, tener plan es obligatorio.
     */
    private Optional<CandidateExercise> pickOneFrom(List<CandidateExercise> pool,
                                                    Set<CandidateExercise> alreadyPicked) {
        var fresh = pool.stream()
                .filter(candidate -> !blocked.contains(candidate.exerciseId()))
                .filter(candidate -> !alreadyPicked.contains(candidate))
                .filter(candidate -> !recentlyUsed.contains(candidate.exerciseId()))
                .toList();

        if (!fresh.isEmpty()) return Optional.of(fresh.get(random.nextInt(fresh.size())));

        var reusable = pool.stream()
                .filter(candidate -> !blocked.contains(candidate.exerciseId()))
                .filter(candidate -> !alreadyPicked.contains(candidate))
                .toList();

        if (reusable.isEmpty()) return Optional.empty();
        return Optional.of(reusable.get(random.nextInt(reusable.size())));
    }
}