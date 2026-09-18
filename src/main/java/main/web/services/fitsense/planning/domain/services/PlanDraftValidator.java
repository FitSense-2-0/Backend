package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.exceptions.InvalidPlanDraftException;
import main.web.services.fitsense.planning.domain.model.valueobjects.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Las validaciones de 19.3 (1-17) y las de coherencia 18-21, aplicadas al
 * borrador antes de persistir.
 * <p>
 * Servicio de dominio puro: no consulta la base. Todo lo que necesita viaja en
 * el contexto, que es exactamente lo que se guarda en input_snapshot, asi que
 * una validacion puede reproducirse meses despues.
 * <p>
 * Devuelve TODOS los problemas, no el primero: esa lista es el insumo del
 * segundo intento de la IA.
 */
@Service
public class PlanDraftValidator {

    /** Pisos de 18.4. No dependen de configuracion porque son limites de seguridad. */
    private static final int MIN_EXERCISES_PER_SESSION = 2;
    private static final int MIN_SETS = 2;
    private static final int MIN_REPS = 6;
    private static final int MIN_DURATION_SECONDS = 20;

    private final SessionDurationEstimator durationEstimator;

    public PlanDraftValidator(SessionDurationEstimator durationEstimator) {
        this.durationEstimator = durationEstimator;
    }

    public void validate(PlanDraft draft, PlanGenerationContext context, int durationToRepsDivisor) {
        var problems = new ArrayList<String>();

        var eligible = context.availableExercises().stream()
                .collect(Collectors.toMap(CandidateExercise::exerciseId, candidate -> candidate,
                        (a, b) -> a));

        validateExercisesAndDays(draft, context, eligible, problems);
        validateFocusCoverage(draft, eligible, problems);
        validateSessions(draft, context, problems);
        validateVolumeTarget(draft, context, durationToRepsDivisor, problems);
        validateLoads(draft, context, problems);
        validatePrescriptionRanges(draft, context, eligible, problems);
        validateRecovery(draft, eligible, problems);
        validateArmSplit(draft, eligible, problems);
        appendSplitSuggestion(context, problems);
        validateReductionDoesNotIncrease(draft, context, eligible, problems);
        validateMinimumDurationAndRest(draft, context, problems);

        if (!problems.isEmpty()) throw new InvalidPlanDraftException(problems);
    }

    /** Validaciones 1, 2, 3, 6 y 12. */
    private void validateExercisesAndDays(PlanDraft draft, PlanGenerationContext context,
                                          Map<Long, CandidateExercise> eligible,
                                          List<String> problems) {
        // 1: solo se puede indicar lo que esta en available_exercises.
        // 6: ningun ejercicio supera la dificultad maxima efectiva.
        int maxDifficulty = context.effectiveMaxDifficulty();
        draft.workouts().stream()
                .flatMap(workout -> workout.exercises().stream())
                .forEach(exercise -> {
                    var candidate = eligible.get(exercise.exerciseId());
                    if (candidate == null)
                        problems.add("V1: el ejercicio %d no esta en available_exercises."
                                .formatted(exercise.exerciseId()));
                    else {
                        if (candidate.difficulty() > maxDifficulty)
                            problems.add("V6: el ejercicio %d tiene dificultad %d y el maximo es %d."
                                    .formatted(exercise.exerciseId(), candidate.difficulty(), maxDifficulty));

                        // 7 (migracion V18, principio 7): el tipo lo fija el catalogo, no
                        // el generador. Sin esto llegaron un estiramiento a 3x7
                        // y un planche a 2x60 s. El motor de reglas cumple por
                        // construccion: copia defaultPrescription.
                        if (exercise.prescriptionType() != null
                                && candidate.defaultPrescription() != null
                                && exercise.prescriptionType() != candidate.defaultPrescription())
                            problems.add("V7: el ejercicio %d se prescribio como %s y debe ir como %s."
                                    .formatted(exercise.exerciseId(), exercise.prescriptionType(),
                                            candidate.defaultPrescription()));
                    }
                });

        // 2: la cantidad de dias coincide con days_per_week.
        int expectedDays = context.effectiveDaysPerWeek();
        if (draft.workouts().size() != expectedDays)
            problems.add("V2: se esperaban %d entrenamientos y el plan trae %d."
                    .formatted(expectedDays, draft.workouts().size()));

        // 3: las fechas caen en available_days y dentro de la semana.
        // 12: no hay dos sesiones el mismo dia.
        var available = new HashSet<>(context.profile().availableDays());
        var seenDates = new HashSet<LocalDate>();
        for (var workout : draft.workouts()) {
            var date = workout.scheduledDate();
            if (!available.contains((short) date.getDayOfWeek().getValue()))
                problems.add("V3: el dia %s no esta en available_days.".formatted(date));
            if (date.isBefore(context.weekStartDate()) || date.isAfter(context.weekEndDate()))
                problems.add("V3: el dia %s cae fuera de la semana planificada.".formatted(date));
            if (!seenDates.add(date))
                problems.add("V12: hay dos entrenamientos el mismo dia (%s).".formatted(date));
        }

        validateConsecutiveFocus(draft, problems);
    }

    /**
     * Validacion 13: no se repite el mismo enfoque en dias consecutivos.
     * <p>
     * Se compara por fecha, no por posicion en la lista: dos sesiones UPPER_BODY
     * el lunes y el jueves son correctas; lunes y martes, no.
     */
    private void validateConsecutiveFocus(PlanDraft draft, List<String> problems) {
        var ordered = draft.workouts().stream()
                .sorted(Comparator.comparing(PlanDraft.DraftWorkout::scheduledDate))
                .toList();

        for (int i = 1; i < ordered.size(); i++) {
            var previous = ordered.get(i - 1);
            var current = ordered.get(i);
            boolean consecutiveDays = previous.scheduledDate().plusDays(1)
                    .equals(current.scheduledDate());
            if (consecutiveDays && previous.focus() == current.focus())
                problems.add("V13: el enfoque %s se repite en dias consecutivos (%s y %s)."
                        .formatted(current.focus(), previous.scheduledDate(), current.scheduledDate()));
        }
    }

    /**
     * Validacion 15: cada entrenamiento debe cubrir de verdad su enfoque.
     * <p>
     * El 20.3 pide "al menos uno de cada body_part del enfoque". El motor de
     * reglas lo cumple por construccion, asi que nunca se convirtio en
     * validacion, y la IA no tenia nada que la obligara: en la primera prueba
     * con el catalogo completo devolvio un FULL_BODY con seis ejercicios de
     * biceps y triceps. Paso las trece validaciones porque ninguna miraba la
     * distribucion.
     * <p>
     * Esto importa mas alla de la calidad del entrenamiento. Un plan que no
     * encaja con lo que el participante espera baja su adherencia, y el sistema
     * interpreta esa caida como falta de compromiso: reduce volumen cuando el
     * problema era la seleccion. La validacion 8 mide si el generador OBEDECE
     * la orden de volumen; esta mide si lo que produce tiene sentido.
     * <p>
     * El minimo se calcula contra lo que el conjunto elegible permite: si el
     * usuario solo tiene material para dos grupos del enfoque, exigir tres
     * dejaria la semana sin plan.
     */
    private void validateFocusCoverage(PlanDraft draft, Map<Long, CandidateExercise> eligible,
                                       List<String> problems) {
        for (var workout : draft.workouts()) {
            var esperados = workout.focus().bodyPartCodes();

            // Cuantos grupos del enfoque tienen material disponible.
            long disponibles = esperados.stream()
                    .filter(code -> eligible.values().stream()
                            .anyMatch(candidate -> code.equals(candidate.bodyPartCode())))
                    .count();

            var cubiertos = workout.exercises().stream()
                    .map(exercise -> eligible.get(exercise.exerciseId()))
                    .filter(java.util.Objects::nonNull)
                    .map(CandidateExercise::bodyPartCode)
                    .filter(esperados::contains)
                    .collect(Collectors.toSet());

            // Cubrir el enfoque no basta si ademas se cuelan grupos ajenos. En
            // la prueba salio un PULL con dos ejercicios de pecho, que es
            // empuje y no traccion: la comprobacion de cobertura no lo veia
            // porque solo contaba cuantos grupos del enfoque estaban presentes.
            //
            // FULL_BODY queda exento: su lista es orientativa y no tiene sentido
            // rechazar un cuerpo completo por incluir gemelos o antebrazos.
            if (workout.focus() != WorkoutFocus.FULL_BODY) {
                var ajenos = workout.exercises().stream()
                        .map(exercise -> eligible.get(exercise.exerciseId()))
                        .filter(java.util.Objects::nonNull)
                        .map(CandidateExercise::bodyPartCode)
                        .filter(code -> !esperados.contains(code))
                        .distinct()
                        .toList();

                if (!ajenos.isEmpty())
                    problems.add(("V15: el entrenamiento del %s tiene enfoque %s pero incluye "
                            + "ejercicios de %s. Ese enfoque solo admite: %s.")
                            .formatted(workout.scheduledDate(), workout.focus(),
                                    ajenos, esperados));
            }

            // Se pide cubrir todo lo que se pueda, con tope en 3: exigir los
            // cinco grupos de FULL_BODY obligaria a sesiones de cinco
            // ejercicios minimo, y con REDUCE_VOLUME puede haber solo dos.
            int minimo = (int) Math.min(2, Math.min(disponibles, workout.exercises().size()));

            if (cubiertos.size() < minimo)
                problems.add(("V15: el entrenamiento del %s tiene enfoque %s y solo trabaja %s. "
                        + "Debe cubrir al menos %d grupos distintos de: %s.")
                        .formatted(workout.scheduledDate(), workout.focus(),
                                cubiertos.isEmpty() ? "ningun grupo del enfoque" : cubiertos,
                                minimo, esperados));

            // Ademas, ningun grupo puede acaparar mas de la mitad de la sesion
            // cuando el enfoque abarca varios.
            if (esperados.size() >= 3 && workout.exercises().size() >= 4) {
                var porGrupo = workout.exercises().stream()
                        .map(exercise -> eligible.get(exercise.exerciseId()))
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.groupingBy(CandidateExercise::bodyPartCode,
                                Collectors.counting()));

                porGrupo.forEach((grupo, cuantos) -> {
                    if (cuantos > workout.exercises().size() / 2)
                        problems.add(("V15: el entrenamiento del %s dedica %d de %d ejercicios a %s. "
                                + "Reparte entre los grupos del enfoque %s.")
                                .formatted(workout.scheduledDate(), cuantos,
                                        workout.exercises().size(), grupo, workout.focus()));
                });
            }
        }
    }

    /** Validaciones 4, 5, 7, 9 y 10. */
    private void validateSessions(PlanDraft draft, PlanGenerationContext context,
                                  List<String> problems) {
        int ceiling = context.profile().maxSessionMinutes();

        for (var workout : draft.workouts()) {
            // 4: ninguna sesion excede session_minutes + 15 %.
            if (workout.expectedDurationMinutes() > ceiling)
                problems.add("V4: el entrenamiento del %s dura %d minutos y el techo es %d."
                        .formatted(workout.scheduledDate(), workout.expectedDurationMinutes(), ceiling));

            // 5: ninguna sesion tiene menos de 2 ejercicios.
            if (workout.exercises().size() < MIN_EXERCISES_PER_SESSION)
                problems.add("V5: el entrenamiento del %s trae %d ejercicios y el minimo es %d."
                        .formatted(workout.scheduledDate(), workout.exercises().size(),
                                MIN_EXERCISES_PER_SESSION));

            for (var exercise : workout.exercises()) {
                validatePrescription(exercise, problems);
            }
        }
    }

    private void validatePrescription(PlanDraft.DraftExercise exercise, List<String> problems) {
        if (exercise.prescriptionType() == null) {
            problems.add("V7: el ejercicio %d no declara prescription_type."
                    .formatted(exercise.exerciseId()));
            return;
        }

        if (exercise.prescriptionType() == PrescriptionType.SETS_REPS) {
            // 7: campos obligatorios del tipo. 9: pisos de series y repeticiones.
            if (exercise.plannedSets() == null || exercise.plannedReps() == null) {
                problems.add("V7: el ejercicio %d es SETS_REPS y le faltan series o repeticiones."
                        .formatted(exercise.exerciseId()));
                return;
            }
            if (exercise.plannedSets() < MIN_SETS)
                problems.add("V9: el ejercicio %d baja de %d series."
                        .formatted(exercise.exerciseId(), MIN_SETS));
            if (exercise.plannedReps() < MIN_REPS)
                problems.add("V9: el ejercicio %d baja de %d repeticiones."
                        .formatted(exercise.exerciseId(), MIN_REPS));
            return;
        }

        // 10: piso de los ejercicios de duracion.
        if (exercise.plannedDurationSeconds() == null)
            problems.add("V7: el ejercicio %d es DURATION y no declara segundos."
                    .formatted(exercise.exerciseId()));
        else if (exercise.plannedDurationSeconds() < MIN_DURATION_SECONDS)
            problems.add("V10: el ejercicio %d baja de %d segundos."
                    .formatted(exercise.exerciseId(), MIN_DURATION_SECONDS));
    }

    /**
     * Validacion 8: si hay ajuste, el volumen semanal cae entre target_volume_min
     * y target_volume_max.
     * <p>
     * Es la validacion que sostiene la tesis. El volumen se RECALCULA sumando las
     * prescripciones; el total_volume que declara la IA no se usa mas que para
     * detectar que se equivoco al contar.
     */
    private void validateVolumeTarget(PlanDraft draft, PlanGenerationContext context,
                                      int durationToRepsDivisor, List<String> problems) {
        var adjustment = context.adjustment();
        if (adjustment == null || !adjustment.isActive()) return;

        int actual = draft.volume(durationToRepsDivisor);
        if (actual < adjustment.targetVolumeMin() || actual > adjustment.targetVolumeMax())
            problems.add("V8: el volumen semanal es %d y debe caer entre %d y %d (objetivo %d)."
                    .formatted(actual, adjustment.targetVolumeMin(),
                            adjustment.targetVolumeMax(), adjustment.targetVolume()));

        if (draft.declaredTotalVolume() != null && draft.declaredTotalVolume() != actual)
            problems.add("V8: total_volume declarado %d no coincide con el real %d."
                    .formatted(draft.declaredTotalVolume(), actual));
    }

    /**
     * Validacion 11 (principio 8, P-1.2): el peso SUGERIDO.
     * <p>
     * target_load_kg es solo una sugerencia para el participante. No entra en la
     * adherencia, el volumen ni el analisis: esta validacion existe para que la
     * sugerencia sea segura, no porque el peso se mida.
     * <ul>
     *   <li>Sin peso anotado la semana anterior, va null: sin dato real, un kilo
     *       prescrito es falsa precision.</li>
     *   <li>Mantener o bajar el peso usado: siempre permitido.</li>
     *   <li>Subir: solo si hizo TODAS las repeticiones, como maximo
     *       max_load_increase_pct (10 %, ACSM 2009), y sin subir series ni
     *       repeticiones de ese ejercicio.</li>
     *   <li>LOWER_LOAD: ningun peso puede superar el usado.</li>
     *   <li>Ejercicios por duracion: sin peso.</li>
     * </ul>
     * El RPE NO se valida aqui: la escala no esta instrumentada y queda como
     * instruccion al modelo.
     */
    private void validateLoads(PlanDraft draft, PlanGenerationContext context,
                               List<String> problems) {
        var previousWeek = context.previousWeek();
        boolean lowerLoad = context.adjustment() != null && context.adjustment().clearsLoad();
        int maxIncreasePct = context.prescription() == null ? 10
                : context.prescription().maxLoadIncreasePctOrDefault();

        draft.workouts().stream()
                .flatMap(workout -> workout.exercises().stream())
                .filter(exercise -> exercise.targetLoadKg() != null)
                .forEach(exercise -> {
                    long id = exercise.exerciseId();
                    BigDecimal target = exercise.targetLoadKg();

                    if (exercise.prescriptionType() == PrescriptionType.DURATION) {
                        problems.add("V11: el ejercicio %d es por duracion y no lleva peso.".formatted(id));
                        return;
                    }

                    var reference = previousWeek == null ? Optional.<PreviousWeekSummary.LoadReference>empty()
                            : previousWeek.lastLoadFor(id);
                    if (reference.isEmpty()) {
                        problems.add(("V11: el ejercicio %d sugiere %s kg y la persona no anoto peso "
                                + "la semana anterior; debe ir en null.").formatted(id, target));
                        return;
                    }

                    BigDecimal used = reference.get().loadKg();
                    if (target.compareTo(used) <= 0) return;

                    if (lowerLoad) {
                        problems.add("V11: el ajuste pide bajar carga y el ejercicio %d sube de %s a %s kg."
                                .formatted(id, used, target));
                        return;
                    }
                    if (!reference.get().fullyCompleted())
                        problems.add(("V11: el ejercicio %d sube de %s a %s kg y la semana anterior no "
                                + "hizo todas las repeticiones.").formatted(id, used, target));

                    BigDecimal ceiling = used.multiply(BigDecimal.valueOf(100 + maxIncreasePct))
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    if (target.compareTo(ceiling) > 0)
                        problems.add("V11: el ejercicio %d sube de %s a %s kg; el maximo es %s kg (+%d %%)."
                                .formatted(id, used, target, ceiling, maxIncreasePct));

                    boolean moreSets = exercise.plannedSets() != null && reference.get().sets() != null
                            && exercise.plannedSets() > reference.get().sets();
                    boolean moreReps = exercise.plannedReps() != null && reference.get().reps() != null
                            && exercise.plannedReps() > reference.get().reps();
                    if (moreSets || moreReps)
                        problems.add(("V11: el ejercicio %d sube el peso y tambien series o repeticiones; "
                                + "solo puede subir una cosa a la vez.").formatted(id));
                });
    }

    /**
     * Validaciones 16 y 17: las repeticiones deben caer en el limite amplio de
     * P-1.0 (antes, en el rango del objetivo), y la duracion declarada debe
     * corresponder al contenido.
     * <p>
     * V17 CAMBIO RESPECTO AL §19.3. Antes exigia que la duracion declarada no
     * bajara del 70 % de session_minutes. Esa regla castigaba al generador que
     * estima con honestidad y premiaba al que copia el numero del perfil: con un
     * ajuste de -30 %, la IA declaro 28 minutos para un contenido de ~25 y fue
     * rechazada, mientras el motor de reglas declaro 45 para un contenido de ~27
     * y paso.
     * <p>
     * El piso desaparece por completo, no solo cuando hay ajuste: la
     * prescripcion por defecto del §20.4 (5 ejercicios de 3x12 con 60 s de
     * descanso) rinde unos 28 minutos, asi que un piso del 70 % sobre 45 dejaria
     * sin plan valido al motor de reglas, que es el respaldo de ultima
     * instancia. Que la prescripcion por defecto no llene la sesion declarada es
     * un problema real, pero se resuelve revisando el §20.4 con el asesor, no
     * bloqueando la generacion.
     * <p>
     * Lo que impide un plan ridiculamente corto sigue siendo la validacion 5
     * (minimo 2 ejercicios), la 9 (minimo 2 series y 6 repeticiones, piso de
     * seguridad del backend; a la IA se le da el min_reps de cada ejercicio) y, cuando
     * hay ajuste, la 8 (el volumen objetivo).
     * <p>
     * Se salta entera si la configuracion activa no trae el bloque prescription.
     * Mejor no validar que inventar limites.
     */
    private void validatePrescriptionRanges(PlanDraft draft, PlanGenerationContext context,
                                            Map<Long, CandidateExercise> eligible,
                                            List<String> problems) {
        var prescription = context.prescription();
        if (prescription == null) return;

        // 16 (migracion V18, principios P-1.0): limite amplio por ejercicio. Las
        // repeticiones las decide el generador; aqui solo se rechaza lo absurdo.
        // Ya no hay rango por objetivo: 15 elevaciones de talon para un perfil
        // de fuerza son coherentes y el rango 4-10 las rechazaba.
        var limites = prescription.repLimits();
        if (limites != null && limites.isComplete()) {
            draft.workouts().stream()
                    .flatMap(workout -> workout.exercises().stream())
                    .filter(exercise -> exercise.prescriptionType() == PrescriptionType.SETS_REPS)
                    .filter(exercise -> exercise.plannedReps() != null)
                    .forEach(exercise -> {
                        int reps = exercise.plannedReps();
                        var candidate = eligible.get(exercise.exerciseId());
                        String zona = candidate == null ? null : candidate.bodyPartCode();
                        // Minimo por zona (migracion V20, provisional): sin esto la IA
                        // puso los 12 ejercicios de la semana a 3x10, gemelos incluidos.
                        int minimo = limites.minRepsFor(zona, context.profile().fitnessLevel());
                        if (reps < minimo || reps > limites.maxReps())
                            // Mensaje en forma de instruccion (plan 29): el reintento
                            // lo lee junto a su propuesta y tiene que saber que poner.
                            problems.add(("V16: el ejercicio %d (%s) lleva %d repeticiones y el limite "
                                    + "es de %d a %d. Ponle entre %d y %d (su min_reps es %d).")
                                    .formatted(exercise.exerciseId(), zona, reps,
                                            minimo, limites.maxReps(), minimo, limites.maxReps(), minimo));
                    });
        }

        // 16 antiguo: rango por objetivo. Solo aplica con configuraciones
        // anteriores a MVP-1.5, para revalidar planes historicos con sus reglas.
        var rango = limites != null && limites.isComplete() ? null
                : prescription.forGoal(context.profile().goalType());
        if (rango != null && rango.minReps() != null && rango.maxReps() != null) {
            draft.workouts().stream()
                    .flatMap(workout -> workout.exercises().stream())
                    .filter(exercise -> exercise.prescriptionType() == PrescriptionType.SETS_REPS)
                    .filter(exercise -> exercise.plannedReps() != null)
                    .forEach(exercise -> {
                        int reps = exercise.plannedReps();
                        if (reps < rango.minReps() || reps > rango.maxReps())
                            problems.add(("V16: el ejercicio %d lleva %d repeticiones y el objetivo "
                                    + "%s exige entre %d y %d.")
                                    .formatted(exercise.exerciseId(), reps,
                                            context.profile().goalType(),
                                            rango.minReps(), rango.maxReps()));
                    });
        }

        // 17: coherencia entre lo declarado y el contenido.
        int tolerancia = prescription.durationTolerancePctOrDefault();
        for (var workout : draft.workouts()) {
            int estimada = durationEstimator.estimateMinutes(workout, prescription);
            if (estimada <= 0) continue;

            double desvio = Math.abs(workout.expectedDurationMinutes() - estimada) * 100.0 / estimada;
            if (desvio > tolerancia)
                problems.add(("V17: el entrenamiento del %s declara %d minutos pero su contenido "
                        + "dura unos %d (desvio de %.0f %%, maximo %d %%). Declara lo que dure el "
                        + "contenido final, despues de corregir lo demas.")
                        .formatted(workout.scheduledDate(), workout.expectedDurationMinutes(),
                                estimada, desvio, tolerancia));
        }
    }

    /**
     * Grupos que necesitan recuperacion entre sesiones. Abdomen, brazos y
     * gemelos quedan fuera: body_part es demasiado grueso (biceps y triceps
     * comparten "upper arms") y son grupos que toleran dias seguidos. Criterio
     * de diseno declarado.
     */
    private static final Set<String> RECOVERY_BODY_PARTS =
            Set.of("chest", "back", "shoulders", "upper legs");

    /**
     * Validacion 18: recuperacion entre dias consecutivos.
     * <ul>
     *   <li>El mismo ejercicio no se repite en dias consecutivos.</li>
     *   <li>Pecho, espalda, hombros y piernas no se trabajan dos dias seguidos.</li>
     * </ul>
     * V13 solo comparaba el NOMBRE del enfoque: FULL_BODY el lunes y UPPER_BODY
     * el martes pasaban aunque repitieran pecho, espalda y hombros, y en la
     * prueba el mismo press y el mismo remo aparecieron lunes y martes.
     */
    private void validateRecovery(PlanDraft draft, Map<Long, CandidateExercise> eligible,
                                  List<String> problems) {
        var ordered = draft.workouts().stream()
                .sorted(Comparator.comparing(PlanDraft.DraftWorkout::scheduledDate))
                .toList();

        for (int i = 1; i < ordered.size(); i++) {
            var previous = ordered.get(i - 1);
            var current = ordered.get(i);
            if (!previous.scheduledDate().plusDays(1).equals(current.scheduledDate())) continue;

            var previousIds = previous.exercises().stream()
                    .map(PlanDraft.DraftExercise::exerciseId).collect(Collectors.toSet());
            current.exercises().stream()
                    .map(PlanDraft.DraftExercise::exerciseId)
                    .filter(previousIds::contains)
                    .distinct()
                    .forEach(id -> problems.add(("V18: el ejercicio %d se repite en dias consecutivos "
                            + "(%s y %s).").formatted(id, previous.scheduledDate(), current.scheduledDate())));

            var previousGroups = recoveryGroupsOf(previous, eligible);
            var shared = recoveryGroupsOf(current, eligible).stream()
                    .filter(previousGroups::contains)
                    .sorted()
                    .toList();
            if (!shared.isEmpty())
                problems.add(("V18: %s se trabaja en dias consecutivos (%s y %s). Alterna tren superior "
                        + "e inferior, o empuje y traccion.")
                        .formatted(shared, previous.scheduledDate(), current.scheduledDate()));
        }
    }

    private static Set<String> recoveryGroupsOf(PlanDraft.DraftWorkout workout,
                                                Map<Long, CandidateExercise> eligible) {
        return workout.exercises().stream()
                .map(exercise -> eligible.get(exercise.exerciseId()))
                .filter(Objects::nonNull)
                .map(CandidateExercise::bodyPartCode)
                .filter(RECOVERY_BODY_PARTS::contains)
                .collect(Collectors.toSet());
    }

    /**
     * Validacion 19: si la orden es REDUCE_VOLUME, un ejercicio que se repite de
     * la semana anterior no sube series ni repeticiones. En la prueba, con -20 %
     * ordenado, la IA subio el press de 10 a 13 y el crunch de 12 a 16.
     * <p>
     * El tope de repeticiones es el mayor entre lo prescrito antes y el minimo
     * de su zona: si el minimo por zona es nuevo, subir hasta el no es progresar.
     */
    private void validateReductionDoesNotIncrease(PlanDraft draft, PlanGenerationContext context,
                                                  Map<Long, CandidateExercise> eligible,
                                                  List<String> problems) {
        var caps = reductionCaps(context);
        if (caps.isEmpty()) return;

        draft.workouts().stream()
                .flatMap(workout -> workout.exercises().stream())
                .filter(exercise -> exercise.prescriptionType() == PrescriptionType.SETS_REPS)
                .forEach(exercise -> {
                    var cap = caps.get(exercise.exerciseId());
                    if (cap == null) return;
                    long id = exercise.exerciseId();
                    if (exercise.plannedSets() != null && exercise.plannedSets() > cap.maxSets())
                        problems.add(("V19: el ejercicio %d lleva %d series y su tope es %d (%s). "
                                + "Maximo: %d series y %d repeticiones.")
                                .formatted(id, exercise.plannedSets(), cap.maxSets(), capReason(cap),
                                        cap.maxSets(), cap.maxReps()));
                    if (exercise.plannedReps() != null && exercise.plannedReps() > cap.maxReps())
                        problems.add(("V19: el ejercicio %d lleva %d repeticiones y su tope es %d (%s). "
                                + "Maximo: %d series y %d repeticiones.")
                                .formatted(id, exercise.plannedReps(), cap.maxReps(), capReason(cap),
                                        cap.maxSets(), cap.maxReps()));
                });
    }

    private static String capReason(ReductionCap cap) {
        return "HOLD".equals(cap.reason())
                ? "no lo completo, no hay registro o el esfuerzo fue alto: no puede subir"
                : "lo completo: puede progresar un poco";
    }

    /**
     * Tope de un ejercicio que se repite en una semana de reduccion.
     *
     * @param reason COMPLETED (lo hizo completo y sin esfuerzo alto: puede
     *               progresar un poco) o HOLD (parcial, saltado, sin registro o
     *               RPE alto: no puede subir)
     */
    public record ReductionCap(long exerciseId, int maxSets, int maxReps, String reason) {}

    /** Progresion permitida en un ejercicio completado durante una semana de reduccion. */
    private static final int REDUCTION_EXTRA_SETS = 1;
    private static final int REDUCTION_EXTRA_REPS = 2;
    private static final int HIGH_SESSION_RPE = 8;

    /**
     * Topes de V19, calculados una sola vez para el validador y para el
     * snapshot (constraints.reduce_volume_caps).
     * <p>
     * AFINADO CON EL DESEMPENO (principio 9). La version anterior prohibia subir
     * en CUALQUIER ejercicio repetido. En la prueba la IA fallo dos veces solo por
     * eso: para llegar al volumen reutilizaba ejercicios de la semana anterior, y
     * sin margen rompia otra regla. Lo coherente depende de como le fue:
     * <ul>
     *   <li>HOLD: no lo completo (parcial o saltado), no hay registro, o ese dia
     *       reporto RPE 8 o mas. No puede subir series ni repeticiones.</li>
     *   <li>COMPLETED: lo hizo completo y sin esfuerzo alto. Puede mantener o
     *       progresar un poco: +1 serie y +2 repeticiones como maximo. Criterio de
     *       diseno: sobrecarga pequena y gradual. El total igual debe bajar (V8).</li>
     * </ul>
     * Si el ejercicio aparecio varias veces, basta una ocurrencia en HOLD para
     * que quede en HOLD. Repeticiones: nunca por debajo del minimo de zona y nivel.
     */
    public static Map<Long, ReductionCap> reductionCaps(PlanGenerationContext context) {
        var adjustment = context.adjustment();
        if (adjustment == null || !adjustment.has(AdjustmentType.REDUCE_VOLUME)) return Map.of();
        var previous = context.previousWeek();
        if (previous == null || previous.prescriptions() == null) return Map.of();

        Map<LocalDate, Short> rpeByDate = new HashMap<>();
        if (previous.workouts() != null)
            previous.workouts().forEach(w -> { if (w.scheduledDate() != null && w.sessionRpe() != null)
                rpeByDate.put(w.scheduledDate(), w.sessionRpe()); });

        Map<Long, Integer> sets = new HashMap<>();
        Map<Long, Integer> reps = new HashMap<>();
        Map<Long, Boolean> hold = new HashMap<>();
        for (var outcome : previous.prescriptions()) {
            if (outcome.exerciseId() == null || !"SETS_REPS".equals(outcome.prescriptionType())) continue;
            long id = outcome.exerciseId();
            if (outcome.sets() != null) sets.merge(id, (int) outcome.sets(), Math::max);
            if (outcome.reps() != null) reps.merge(id, (int) outcome.reps(), Math::max);

            Short rpe = outcome.scheduledDate() == null ? null : rpeByDate.get(outcome.scheduledDate());
            boolean completedEasy = "COMPLETED".equals(outcome.exerciseStatus())
                    && (rpe == null || rpe < HIGH_SESSION_RPE);
            hold.merge(id, !completedEasy, Boolean::logicalOr);
        }

        var bodyParts = new HashMap<Long, String>();
        context.availableExercises().forEach(c -> bodyParts.put(c.exerciseId(), c.bodyPartCode()));
        var limites = context.prescription() == null ? null : context.prescription().repLimits();
        String level = context.profile() == null ? null : context.profile().fitnessLevel();

        var caps = new TreeMap<Long, ReductionCap>();
        for (var id : sets.keySet()) {
            if (!reps.containsKey(id)) continue;
            boolean isHold = hold.getOrDefault(id, true);
            int floor = limites == null ? 0 : limites.minRepsFor(bodyParts.get(id), level);
            int maxSets = sets.get(id) + (isHold ? 0 : REDUCTION_EXTRA_SETS);
            int maxReps = Math.max(reps.get(id) + (isHold ? 0 : REDUCTION_EXTRA_REPS), floor);
            caps.put(id, new ReductionCap(id, maxSets, maxReps, isHold ? "HOLD" : "COMPLETED"));
        }
        return caps;
    }

    /**
     * Validaciones 20 y 21: duracion minima y descanso maximo.
     * <p>
     * 20. Sin orden de volumen (primera semana), el contenido de cada sesion
     * debe llegar al session_minutes_floor_pct (70 %) de los minutos que pidio la
     * persona. El parametro existia desde V12 y ninguna validacion lo usaba: en
     * la prueba, 45 minutos pedidos dieron sesiones de 22-27. Cuando hay orden
     * de volumen NO se aplica: el volumen manda y exigir ademas un piso de tiempo
     * solo podria cumplirse inflando descansos.
     * <p>
     * 21. Ningun descanso supera max_rest_seconds (180 s), para que la duracion
     * se consiga con trabajo y no con pausas.
     */
    private void validateMinimumDurationAndRest(PlanDraft draft, PlanGenerationContext context,
                                                List<String> problems) {
        var prescription = context.prescription();
        if (prescription == null) return;

        int maxRest = prescription.maxRestSecondsOrDefault();
        draft.workouts().stream()
                .flatMap(workout -> workout.exercises().stream())
                .filter(exercise -> exercise.restSeconds() != null && exercise.restSeconds() > maxRest)
                .forEach(exercise -> problems.add("V21: el ejercicio %d descansa %d s y el maximo es %d s."
                        .formatted(exercise.exerciseId(), exercise.restSeconds(), maxRest)));

        int floor = minimumSessionMinutes(context);
        if (floor <= 0) return;
        for (var workout : draft.workouts()) {
            int estimada = durationEstimator.estimateMinutes(workout, prescription);
            if (estimada < floor)
                problems.add(("V20: el entrenamiento del %s dura unos %d minutos y el minimo es %d "
                        + "(%d %% de %d): le faltan unos %d minutos. Agrega un ejercicio del enfoque "
                        + "o una serie a los que ya tiene, no descanso.")
                        .formatted(workout.scheduledDate(), estimada, floor,
                                prescription.floorPct(), context.effectiveSessionMinutes(),
                                floor - estimada));
        }
    }

    /** 0 cuando no aplica: hay orden de volumen o no hay configuracion. */
    public static int minimumSessionMinutes(PlanGenerationContext context) {
        if (context.prescription() == null) return 0;
        if (context.adjustment() != null && context.adjustment().isActive()) return 0;
        return context.effectiveSessionMinutes() * context.prescription().floorPct() / 100;
    }

    /**
     * Validacion 22: en "upper arms", PUSH solo admite triceps y PULL solo
     * biceps. body_part no distingue uno de otro; target_muscle si. Con
     * target_muscle desconocido no se rechaza.
     */
    private void validateArmSplit(PlanDraft draft, Map<Long, CandidateExercise> eligible,
                                  List<String> problems) {
        for (var workout : draft.workouts()) {
            if (workout.focus() != WorkoutFocus.PUSH && workout.focus() != WorkoutFocus.PULL) continue;
            String forbidden = workout.focus() == WorkoutFocus.PUSH ? "biceps" : "triceps";
            workout.exercises().stream()
                    .map(exercise -> eligible.get(exercise.exerciseId()))
                    .filter(Objects::nonNull)
                    .filter(candidate -> "upper arms".equals(candidate.bodyPartCode()))
                    .filter(candidate -> candidate.targetMuscle() != null
                            && candidate.targetMuscle().toLowerCase(Locale.ROOT).contains(forbidden))
                    .forEach(candidate -> problems.add(("V22: el entrenamiento %s del %s incluye el ejercicio %d "
                            + "de %s; %s admite solo %s.")
                            .formatted(workout.focus(), workout.scheduledDate(), candidate.exerciseId(),
                                    forbidden, workout.focus(),
                                    workout.focus() == WorkoutFocus.PUSH ? "triceps" : "biceps")));
        }
    }

    /**
     * Si hubo errores de division (V13 o V18), se agrega la division que cumple:
     * "alterna tren superior e inferior" no le dice al modelo que dia es cual,
     * y en la prueba lo corrigio moviendo el problema al tren inferior.
     */
    private void appendSplitSuggestion(PlanGenerationContext context, List<String> problems) {
        boolean splitProblem = problems.stream().anyMatch(p -> p.startsWith("V13") || p.startsWith("V18"));
        if (!splitProblem || context.profile() == null || context.profile().availableDays() == null) return;
        var split = context.suggestedSplit();
        if (split.isEmpty()) return;
        problems.add("DIVISION: usa estas fechas y enfoques, que cumplen V13 y V18: "
                + WeeklySplitPlanner.describe(split) + ".");
    }
}