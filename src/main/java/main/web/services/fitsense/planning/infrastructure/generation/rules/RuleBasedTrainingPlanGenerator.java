package main.web.services.fitsense.planning.infrastructure.generation.rules;

import main.web.services.fitsense.planning.domain.model.valueobjects.*;
import main.web.services.fitsense.planning.domain.services.SessionDurationEstimator;
import main.web.services.fitsense.planning.domain.services.TrainingPlanGenerator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.*;

/**
 * Generador determinista de la seccion 20. Es lo primero que se construye y
 * queda permanentemente como respaldo cuando la IA falla dos veces (19.4).
 * <p>
 * Es deliberadamente TONTO: reparte siempre igual, primero repeticiones, luego
 * series, y solo al final quita ejercicios. Esa previsibilidad no es una
 * limitacion, es la condicion de control del estudio: la diferencia entre este
 * reparto fijo y el que elige la IA segun lo que el usuario cumplio es
 * precisamente lo que la tesis compara.
 * <p>
 * DESVIACION DOCUMENTADA: el 20.3 dice "elige ejercicios al azar". Aqui el azar
 * lleva semilla derivada de usuario y semana, no del reloj. La distribucion es
 * la misma, pero regenerar la misma semana produce el mismo plan, lo que permite
 * reproducir un caso al depurar o ante un reclamo de un participante.
 * <p>
 * DESVIACION DOCUMENTADA (V14): el 20.4 dice que expected_duration_minutes se
 * toma de session_minutes. Ya no: se declara la duracion ESTIMADA a partir del
 * contenido. Copiar el numero del perfil hacia que las sesiones de este
 * generador pesaran mas que las de la IA para el mismo trabajo, y ese peso es
 * el de la metrica primaria del estudio.
 */
@Component
public class RuleBasedTrainingPlanGenerator implements TrainingPlanGenerator {

    private static final int EXERCISES_PER_SESSION = 5;
    private static final int EXERCISES_SHORT_SESSION = 4;
    private static final int SHORT_SESSION_MINUTES = 30;

    /** 20.4: los de duracion van 3 series de 40 segundos con 45 de descanso. */
    private static final short DURATION_SETS = 3;
    private static final int DURATION_SECONDS = 40;
    private static final short DURATION_REST_SECONDS = 45;

    private final int durationToRepsDivisor;
    private final SessionDurationEstimator durationEstimator;

    public RuleBasedTrainingPlanGenerator(
            @Value("${fitsense.volume.duration-to-reps-divisor:30}") int durationToRepsDivisor,
            SessionDurationEstimator durationEstimator) {
        this.durationToRepsDivisor = durationToRepsDivisor;
        this.durationEstimator = durationEstimator;
    }

    @Override
    public GenerationSource source() {
        return GenerationSource.RULE_ENGINE;
    }

    @Override
    public PlanDraft generate(PlanGenerationContext context, List<String> previousProblems) {
        var profile = context.profile();
        int days = Math.min(context.effectiveDaysPerWeek(), profile.availableDays().size());
        var focuses = focusSequence(days);
        var dates = scheduleDates(context.weekStartDate(), profile.availableDays(), days);

        int sessionMinutes = context.effectiveSessionMinutes();
        int exercisesPerSession = sessionMinutes < SHORT_SESSION_MINUTES
                ? EXERCISES_SHORT_SESSION : EXERCISES_PER_SESSION;

        var random = new Random(seedFor(context));
        var recentlyUsed = new HashSet<>(context.previousWeek().exerciseIdsUsedLast7Days());
        var selector = new ExerciseSelector(context.availableExercises(),
                context.effectiveMaxDifficulty(), recentlyUsed, random);

        var prescription = Prescription.forGoal(profile.goalType());

        var workouts = new ArrayList<PlanDraft.DraftWorkout>();
        for (int i = 0; i < dates.size(); i++) {
            var focus = focuses.get(i);
            var picked = selector.pick(focus, exercisesPerSession);

            var exercises = new ArrayList<PlanDraft.DraftExercise>();
            for (var candidate : picked) {
                exercises.add(toDraftExercise(candidate, prescription, context));
            }
            // Provisional: la duracion definitiva se calcula despues de reducir
            // el volumen, cuando el contenido ya es el final.
            workouts.add(new PlanDraft.DraftWorkout(dates.get(i), focus, nameOf(focus),
                    sessionMinutes, exercises));
        }

        var draft = new PlanDraft(source(), modelName(),
                "Semana %d".formatted(context.weekNumber()), null, null, workouts);

        // El ajuste se aplica sobre el borrador ya armado, no durante la
        // seleccion: asi el reparto de 20.5 opera sobre el mismo plan base que
        // se habria generado sin ajuste, y la reduccion es comparable.
        var adjusted = new VolumeReducer(durationToRepsDivisor).apply(draft, context);
        return withRationale(withEstimatedDurations(adjusted, context), context);
    }

    // ------------------------------------------------------------------- 20.1

    private List<WorkoutFocus> focusSequence(int days) {
        return switch (days) {
            case 1 -> List.of(WorkoutFocus.FULL_BODY);
            case 2 -> List.of(WorkoutFocus.FULL_BODY, WorkoutFocus.FULL_BODY);
            case 3 -> List.of(WorkoutFocus.FULL_BODY, WorkoutFocus.FULL_BODY, WorkoutFocus.FULL_BODY);
            case 4 -> List.of(WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY,
                    WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY);
            case 5 -> List.of(WorkoutFocus.PUSH, WorkoutFocus.PULL, WorkoutFocus.LEGS,
                    WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY);
            // 7 se fuerza a 6; el septimo dia queda de descanso.
            default -> List.of(WorkoutFocus.PUSH, WorkoutFocus.PULL, WorkoutFocus.LEGS,
                    WorkoutFocus.PUSH, WorkoutFocus.PULL, WorkoutFocus.LEGS);
        };
    }

    /**
     * Primeros days_per_week valores de available_days, separando los
     * consecutivos cuando la lista lo permite (20.1).
     * <p>
     * Separar importa por la validacion 13: con FULL_BODY repetido no hay
     * conflicto, pero con la rotacion de 6 dias dos PUSH seguidos la fallarian.
     */
    private List<LocalDate> scheduleDates(LocalDate weekStart, List<Short> availableDays, int days) {
        var sorted = availableDays.stream().sorted().toList();
        if (days <= 0 || sorted.isEmpty()) return List.of();
        if (days >= sorted.size())
            return sorted.stream().map(day -> weekStart.plusDays(day - 1L)).toList();

        var chosen = new ArrayList<Short>();
        double step = (double) sorted.size() / days;
        for (int i = 0; i < days; i++) {
            int index = Math.min((int) Math.round(i * step), sorted.size() - 1);
            short day = sorted.get(index);
            if (!chosen.contains(day)) chosen.add(day);
        }
        for (short day : sorted) {
            if (chosen.size() >= days) break;
            if (!chosen.contains(day)) chosen.add(day);
        }

        return chosen.stream().sorted()
                .map(day -> weekStart.plusDays(day - 1L))
                .toList();
    }

    // ------------------------------------------------------------------- 20.4

    private PlanDraft.DraftExercise toDraftExercise(CandidateExercise candidate,
                                                    Prescription prescription,
                                                    PlanGenerationContext context) {
        if (candidate.defaultPrescription() == PrescriptionType.DURATION) {
            return new PlanDraft.DraftExercise(candidate.exerciseId(), PrescriptionType.DURATION,
                    DURATION_SETS, null, DURATION_SECONDS, null, DURATION_REST_SECONDS, null);
        }

        // Peso SUGERIDO (principio 8, P-1.2): el motor de reglas copia el ultimo
        // peso que anoto la persona y nunca lo sube. Asi no pierde la referencia
        // aunque la IA falle. Sin peso anotado, o con LOWER_LOAD, va null y el
        // usuario elige su peso (20.5). No es una medida: solo una sugerencia.
        boolean lowerLoad = context.adjustment() != null && context.adjustment().clearsLoad();
        var suggestedLoad = lowerLoad || context.previousWeek() == null ? null
                : context.previousWeek().lastLoadFor(candidate.exerciseId())
                .map(PreviousWeekSummary.LoadReference::loadKg)
                .orElse(null);

        return new PlanDraft.DraftExercise(candidate.exerciseId(), PrescriptionType.SETS_REPS,
                prescription.sets(), prescription.reps(), null, suggestedLoad,
                prescription.restSeconds(), null);
    }

    /**
     * Declara la duracion estimada en vez de copiar session_minutes (§20.4
     * modificado en V14).
     * <p>
     * Se aplica DESPUES de la reduccion de volumen: si se estimara antes, el
     * numero declarado corresponderia a un contenido que ya no existe.
     * <p>
     * El motivo no es cosmetico. expected_duration_minutes es el peso de la
     * adherencia ponderada. Copiar el numero del perfil hacia que las sesiones
     * del motor de reglas pesaran sistematicamente mas que las de la IA para el
     * mismo trabajo, y eso es un sesgo de medicion dependiente del brazo.
     */
    private PlanDraft withEstimatedDurations(PlanDraft draft, PlanGenerationContext context) {
        var prescription = context.prescription();
        if (prescription == null) return draft;

        // La validacion 4 sigue mandando: nunca por encima del techo del perfil.
        int techo = context.profile().maxSessionMinutes();

        var workouts = draft.workouts().stream()
                .map(workout -> new PlanDraft.DraftWorkout(
                        workout.scheduledDate(),
                        workout.focus(),
                        workout.name(),
                        Math.min(techo, durationEstimator.estimateMinutes(workout, prescription)),
                        workout.exercises()))
                .toList();

        return new PlanDraft(draft.source(), draft.modelName(), draft.planName(),
                draft.declaredTotalVolume(), draft.rationale(), workouts);
    }

    private String nameOf(WorkoutFocus focus) {
        return switch (focus) {
            case FULL_BODY -> "Cuerpo completo";
            case UPPER_BODY -> "Tren superior";
            case LOWER_BODY -> "Tren inferior";
            case PUSH -> "Empuje";
            case PULL -> "Traccion";
            case LEGS -> "Piernas";
            case CORE -> "Core";
        };
    }

    private PlanDraft withRationale(PlanDraft draft, PlanGenerationContext context) {
        var adjustment = context.adjustment();
        String rationale = adjustment != null && adjustment.isActive()
                ? "Plan generado por el motor de reglas ajustando el volumen a %d repeticiones equivalentes."
                .formatted(adjustment.targetVolume())
                : "Plan generado por el motor de reglas.";

        return new PlanDraft(draft.source(), draft.modelName(), draft.planName(),
                draft.volume(durationToRepsDivisor), rationale, draft.workouts());
    }

    /** Semilla estable: mismo usuario y misma semana, mismo plan. */
    private long seedFor(PlanGenerationContext context) {
        return context.userId() * 1_000_003L + context.weekStartDate().toEpochDay();
    }
}