package main.web.services.fitsense.planning.infrastructure.generation.rules;

import main.web.services.fitsense.planning.domain.model.valueobjects.CandidateExercise;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutFocus;

import java.util.*;

/**
 * Seleccion de ejercicios de 20.3: al menos uno de cada body_part del enfoque,
 * y el resto repartido entre esos mismos grupos.
 * <p>
 * DESVIACION DOCUMENTADA: el 20.3 dice "se completa al azar". Ya no. El azar
 * dentro del enfoque concentraba en el grupo mas poblado del catalogo —waist
 * tiene 84 ejercicios de peso corporal y lower legs 13— y eso hacia que
 * LOWER_BODY cayera sistematicamente por la regla de la mitad de V15. El
 * respaldo determinista incumplia las mismas validaciones que el generador al
 * que respalda, asi que dejaba al participante sin plan.
 * <p>
 * PRIORIDAD POR GRUPO (criterio de diseno provisional, plan 29). El reparto por
 * turnos trataba igual a todos los grupos del enfoque: para llegar a la
 * duracion minima, un LOWER_BODY de 8 ejercicios salio con 3 de muslo, 3 de
 * gemelos y 2 de abdomen, y sin sentadilla. Ahora:
 * <ul>
 *   <li>Grupos grandes (chest, back, shoulders, upper legs) pesan el doble que
 *       los pequenos al repartir: reciben unos dos ejercicios por cada uno de
 *       un grupo pequeno. A igualdad de proporcion va primero el pequeno.</li>
 *   <li>Grupos pequenos con tope por sesion: lower legs 1, waist 2, upper arms 2.
 *       El tope solo aplica si el enfoque tiene algun grupo grande (CORE no).</li>
 *   <li>Si con esos topes no se puede completar, se relajan los topes de los
 *       grupos pequenos, nunca la regla de la mitad de V15: tener plan sigue
 *       siendo obligatorio.</li>
 * </ul>
 * body_part es una aproximacion. Se reemplaza por la categoria de movimiento
 * cuando el catalogo este clasificado.
 */
class ExerciseSelector {

    /** Grupos grandes: pesan el doble al repartir y no tienen tope propio. */
    static final Set<String> MAJOR_GROUPS = Set.of("chest", "back", "shoulders", "upper legs");

    /** Tope por sesion de los grupos pequenos (provisional, criterio de diseno). */
    static final Map<String, Integer> MAX_PER_SESSION = Map.of(
            "lower legs", 1,
            "waist", 2,
            "upper arms", 2);

    private static final int MAJOR_WEIGHT = 2;
    private static final int MINOR_WEIGHT = 1;

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

    static boolean isMajor(String bodyPartCode) {
        return bodyPartCode != null && MAJOR_GROUPS.contains(bodyPartCode);
    }

    List<CandidateExercise> pick(WorkoutFocus focus, int count) {
        var picked = new LinkedHashSet<CandidateExercise>();
        var grupos = focus.bodyPartCodes();

        // V15 rechaza cuando un grupo supera la mitad de la sesion (division
        // entera), y solo si el enfoque abarca 3 o mas grupos y la sesion tiene
        // 4 o mas ejercicios. Fuera de ese caso no hay tope que respetar.
        int tope = halfCap(grupos, count);
        var porGrupo = new HashMap<String, Integer>();

        // 1. Cobertura (20.3): uno de cada grupo, los grandes primero. Si la
        //    sesion es mas corta que el numero de grupos, quedan fuera los
        //    pequenos, no los grandes.
        var cobertura = grupos.stream()
                .sorted(Comparator.comparing((String code) -> isMajor(code) ? 0 : 1)
                        .thenComparingInt(grupos::indexOf))
                .toList();
        for (var code : cobertura) {
            if (picked.size() >= count) break;
            if (porGrupo.getOrDefault(code, 0) + 1 > tope) continue;
            var candidate = pickOneFrom(pool(code, focus), picked);
            if (candidate.isEmpty()) continue;
            picked.add(candidate.get());
            porGrupo.merge(code, 1, Integer::sum);
        }

        // 2. Resto por prioridad: primero con los topes de grupos pequenos y,
        //    solo si no alcanza, sin ellos.
        for (boolean respetarTopes : new boolean[]{true, false}) {
            while (picked.size() < count) {
                var next = next(focus, picked, porGrupo, tope, respetarTopes);
                if (next.isEmpty()) break;
                picked.add(next.get());
                porGrupo.merge(next.get().bodyPartCode(), 1, Integer::sum);
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
     *
     * @param respetarTopes false solo como ultimo recurso, cuando con los topes
     *                      de grupos pequenos la sesion no llega al minimo
     */
    Optional<CandidateExercise> pickAdditional(WorkoutFocus focus, List<CandidateExercise> current,
                                               boolean respetarTopes) {
        var picked = new LinkedHashSet<>(current);
        var porGrupo = new HashMap<String, Integer>();
        current.forEach(candidate -> porGrupo.merge(candidate.bodyPartCode(), 1, Integer::sum));
        int tope = halfCap(focus.bodyPartCodes(), current.size() + 1);
        return next(focus, picked, porGrupo, tope, respetarTopes);
    }

    /**
     * Siguiente ejercicio por prioridad: el grupo con menos ejercicios en
     * proporcion a su peso; a igualdad, el pequeno antes que el grande (asi un
     * tren superior de 8 lleva biceps y triceps en vez de un tercer pecho), y
     * despues el orden del enfoque.
     */
    private Optional<CandidateExercise> next(WorkoutFocus focus, Set<CandidateExercise> picked,
                                             Map<String, Integer> porGrupo, int tope,
                                             boolean respetarTopes) {
        var grupos = focus.bodyPartCodes();
        boolean aplicanTopes = respetarTopes && grupos.stream().anyMatch(ExerciseSelector::isMajor);

        var ordenados = grupos.stream()
                .sorted(Comparator.comparingDouble((String code) ->
                                porGrupo.getOrDefault(code, 0) / (double) weight(code))
                        .thenComparing(code -> isMajor(code) ? 1 : 0)
                        .thenComparingInt(grupos::indexOf))
                .toList();

        for (var code : ordenados) {
            int actuales = porGrupo.getOrDefault(code, 0);
            if (actuales + 1 > tope) continue;
            if (aplicanTopes && MAX_PER_SESSION.containsKey(code)
                    && actuales + 1 > MAX_PER_SESSION.get(code)) continue;
            var candidate = pickOneFrom(pool(code, focus), picked);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    private static int weight(String code) {
        return isMajor(code) ? MAJOR_WEIGHT : MINOR_WEIGHT;
    }

    private static int halfCap(List<String> grupos, int sessionSize) {
        return (grupos.size() >= 3 && sessionSize >= 4) ? sessionSize / 2 : sessionSize;
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