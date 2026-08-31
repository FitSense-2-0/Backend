package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.exceptions.InvalidPlanDraftException;
import main.web.services.fitsense.planning.domain.model.valueobjects.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Las trece validaciones de 19.3, aplicadas al borrador antes de persistir.
 * <p>
 * Servicio de dominio puro y sin estado: no consulta la base. Todo lo que
 * necesita viaja en el contexto, que es exactamente lo que se guarda en
 * input_snapshot, asi que una validacion puede reproducirse meses despues.
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
                    else if (candidate.difficulty() > maxDifficulty)
                        problems.add("V6: el ejercicio %d tiene dificultad %d y el maximo es %d."
                                .formatted(exercise.exerciseId(), candidate.difficulty(), maxDifficulty));
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
     * Validacion 11: si el ajuste incluye LOWER_LOAD, ninguna target_load_kg
     * puede superar la de la semana anterior para el mismo ejercicio.
     */
    private void validateLoads(PlanDraft draft, PlanGenerationContext context,
                               List<String> problems) {
        if (context.adjustment() == null || !context.adjustment().clearsLoad()) return;

        var previousLoads = context.previousWeek().prescriptions().stream()
                .filter(prescription -> prescription.loadKg() != null)
                .collect(Collectors.toMap(
                        PreviousWeekSummary.PrescriptionOutcome::exerciseId,
                        PreviousWeekSummary.PrescriptionOutcome::loadKg,
                        (a, b) -> a.max(b)));

        draft.workouts().stream()
                .flatMap(workout -> workout.exercises().stream())
                .filter(exercise -> exercise.targetLoadKg() != null)
                .forEach(exercise -> {
                    BigDecimal previous = previousLoads.get(exercise.exerciseId());
                    if (previous != null && exercise.targetLoadKg().compareTo(previous) > 0)
                        problems.add("V11: el ajuste pide bajar carga y el ejercicio %d sube de %s a %s kg."
                                .formatted(exercise.exerciseId(), previous, exercise.targetLoadKg()));
                });
    }
}
