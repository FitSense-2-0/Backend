package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.model.valueobjects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Corrige el tipo de prescripcion de la propuesta de la IA antes de validarla.
 * <p>
 * SETS_REPS o DURATION no es una decision: es un dato del catalogo que ya viaja
 * en available_exercises[].prescription_type. Cuando el modelo pone otro, no
 * esta eligiendo mal, esta copiando mal. En el plan 36 un solo campo asi (el
 * ejercicio 900, isometrico, prescrito por repeticiones) tiro un plan que
 * cumplia todo lo demas.
 * <p>
 * DESVIACION DOCUMENTADA: el backend deja de solo rechazar y pasa a CORREGIR la
 * salida del modelo, en este unico aspecto. Se limita a lo mecanico —el tipo y
 * los campos que ese tipo exige— y nunca toca la seleccion de ejercicios, el
 * reparto ni el volumen, que si son decisiones. Cada correccion se registra en
 * el log, asi que la tasa de planes validos SIN normalizar sigue siendo
 * medible: es lo que hay que reportar en la tesis junto a la tasa final.
 * <p>
 * Los valores de relleno son los mismos que usa el motor de reglas, para que un
 * ejercicio de duracion se vea igual venga de donde venga.
 */
@Service
public class PlanDraftNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PlanDraftNormalizer.class);

    /** Mismos valores que el motor de reglas para los ejercicios de duracion. */
    public static final short DURATION_SETS = 3;
    public static final int DURATION_SECONDS = 40;
    public static final short DURATION_REST_SECONDS = 45;

    /** Series minimas de V9 cuando la propuesta no trae un valor usable. */
    private static final short MIN_SETS = 2;

    public record Result(PlanDraft draft, List<String> corrections) {
        public boolean isClean() {
            return corrections.isEmpty();
        }
    }

    public Result normalize(PlanDraft draft, PlanGenerationContext context) {
        var catalogo = context.availableExercises().stream()
                .collect(Collectors.toMap(CandidateExercise::exerciseId, Function.identity(),
                        (a, b) -> a));
        var correcciones = new ArrayList<String>();

        var workouts = draft.workouts().stream()
                .map(workout -> new PlanDraft.DraftWorkout(workout.scheduledDate(), workout.focus(),
                        workout.name(), workout.expectedDurationMinutes(),
                        workout.exercises().stream()
                                .map(exercise -> normalizeExercise(exercise, catalogo, context, correcciones))
                                .toList()))
                .toList();

        if (correcciones.isEmpty()) return new Result(draft, List.of());

        correcciones.forEach(correccion ->
                log.info("Propuesta de IA normalizada para el usuario {}: {}",
                        context.userId(), correccion));

        return new Result(new PlanDraft(draft.source(), draft.modelName(), draft.planName(),
                draft.declaredTotalVolume(), draft.rationale(), workouts), List.copyOf(correcciones));
    }

    private PlanDraft.DraftExercise normalizeExercise(PlanDraft.DraftExercise exercise,
                                                      Map<Long, CandidateExercise> catalogo,
                                                      PlanGenerationContext context,
                                                      List<String> correcciones) {
        var candidate = catalogo.get(exercise.exerciseId());

        // Sin ejercicio en el catalogo no hay nada que corregir: es V1 y ese
        // rechazo debe llegar al modelo tal cual.
        if (candidate == null || candidate.defaultPrescription() == null) return exercise;

        var esperado = candidate.defaultPrescription();
        if (exercise.prescriptionType() == esperado) return exercise;

        if (esperado == PrescriptionType.DURATION) {
            correcciones.add(("ejercicio %d (%s): SETS_REPS -> DURATION, %d series de %d s")
                    .formatted(exercise.exerciseId(), candidate.name(),
                            setsOf(exercise, DURATION_SETS), DURATION_SECONDS));
            return new PlanDraft.DraftExercise(exercise.exerciseId(), PrescriptionType.DURATION,
                    setsOf(exercise, DURATION_SETS), null, DURATION_SECONDS, exercise.targetLoadKg(),
                    DURATION_REST_SECONDS, exercise.notes());
        }

        short reps = repsFor(candidate, context);
        correcciones.add(("ejercicio %d (%s): DURATION -> SETS_REPS, %d series de %d repeticiones")
                .formatted(exercise.exerciseId(), candidate.name(),
                        setsOf(exercise, MIN_SETS), reps));
        return new PlanDraft.DraftExercise(exercise.exerciseId(), PrescriptionType.SETS_REPS,
                setsOf(exercise, MIN_SETS), reps, null, exercise.targetLoadKg(),
                exercise.restSeconds(), exercise.notes());
    }

    /** Conserva las series de la propuesta si son usables; si no, el valor por defecto. */
    private short setsOf(PlanDraft.DraftExercise exercise, short porDefecto) {
        return exercise.plannedSets() == null || exercise.plannedSets() < MIN_SETS
                ? porDefecto : exercise.plannedSets();
    }

    /** Repeticiones de partida del objetivo, nunca por debajo del minimo de V16. */
    private short repsFor(CandidateExercise candidate, PlanGenerationContext context) {
        int base = TargetPrescription.forGoal(context.profile().goalType()).reps();
        var limites = context.prescription() == null ? null : context.prescription().repLimits();
        if (limites == null || !limites.isComplete()) return (short) base;
        return (short) Math.min(limites.maxReps(), Math.max(base,
                limites.minRepsFor(candidate.bodyPartCode(), context.profile().fitnessLevel())));
    }
}
