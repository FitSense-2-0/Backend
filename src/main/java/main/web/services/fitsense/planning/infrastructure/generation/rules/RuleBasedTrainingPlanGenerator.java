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
 * Es deliberadamente simple y predecible: sus reglas se pueden leer y
 * reproducir. Ya NO es una condicion de control (el estudio es de un solo grupo,
 * decision 6): es el respaldo que produce la semana cuando la IA falla. Por eso
 * tiene que ser coherente para la persona, no solo valido. Las semanas que
 * genere se reportan aparte y el analisis se repite sin ellas.
 * <p>
 * COHERENCIA (plan 29, criterio de diseno provisional):
 * <ul>
 *   <li>Repeticiones segun la zona: gemelos y abdomen llevan
 *       {@value #SMALL_ZONE_EXTRA_REPS} mas que la base del objetivo
 *       (principio 2: un ejercicio pequeno necesita mas repeticiones), nunca
 *       bajo el minimo de zona y nivel ni sobre el maximo.</li>
 *   <li>Para llegar a la duracion minima: primero ejercicios con los topes de
 *       grupos pequenos (ExerciseSelector), luego 3 series en los ejercicios de
 *       grupos grandes, luego en los pequenos y, solo si aun falta, ejercicios
 *       sin esos topes.</li>
 *   <li>En semana de reduccion, los ejercicios repetidos respetan los topes de
 *       V19 antes de reducir el volumen.</li>
 * </ul>
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
    /** Tope al completar la duracion minima (V20). */
    private static final int MAX_EXERCISES_PER_SESSION = 8;

    /** Series maximas al completar la duracion (ACSM 2009: 1-3 para principiantes). */
    private static final short FILL_MAX_SETS = 3;

    /**
     * Repeticiones extra en zonas pequenas (lower legs, waist) sobre la base del
     * objetivo. Criterio de diseno provisional, principio 2; se reemplaza con la
     * categoria de movimiento del catalogo.
     */
    static final int SMALL_ZONE_EXTRA_REPS = 3;
    private static final Set<String> SMALL_ZONES = Set.of("lower legs", "waist");


    /** 20.4: los de duracion van 3 series de 40 segundos con 45 de descanso. */
    // Definidos en PlanDraftNormalizer: un ejercicio de duracion se ve igual
    // venga del motor de reglas o de una propuesta de IA ya normalizada.
    private static final short DURATION_SETS =
            main.web.services.fitsense.planning.domain.services.PlanDraftNormalizer.DURATION_SETS;
    private static final int DURATION_SECONDS =
            main.web.services.fitsense.planning.domain.services.PlanDraftNormalizer.DURATION_SECONDS;
    private static final short DURATION_REST_SECONDS =
            main.web.services.fitsense.planning.domain.services.PlanDraftNormalizer.DURATION_REST_SECONDS;

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
        // Division semanal (WeeklySplitPlanner): la misma que recibe la IA como
        // suggested_split y la que usa el validador para sus mensajes.
        var split = context.suggestedSplit();
        var dates = split.stream().map(main.web.services.fitsense.planning.domain.services
                .WeeklySplitPlanner.PlannedSession::date).toList();
        var focuses = split.stream().map(main.web.services.fitsense.planning.domain.services
                .WeeklySplitPlanner.PlannedSession::focus).toList();

        int sessionMinutes = context.effectiveSessionMinutes();
        int exercisesPerSession = sessionMinutes < SHORT_SESSION_MINUTES
                ? EXERCISES_SHORT_SESSION : EXERCISES_PER_SESSION;

        var random = new Random(seedFor(context));
        var recentlyUsed = new HashSet<>(context.previousWeek().exerciseIdsUsedLast7Days());
        var selector = new ExerciseSelector(context.availableExercises(),
                context.effectiveMaxDifficulty(), recentlyUsed, random);

        var prescription = TargetPrescription.forGoal(profile.goalType());

        // El plan BASE se arma siempre igual, con o sin ajuste: el reductor solo
        // sabe bajar volumen, asi que si la semana 1 se completo hasta el minimo
        // de minutos y la semana 2 no, el objetivo quedaria por encima del base
        // y el reductor destruiria la sesion. Por eso el minimo se calcula aqui
        // sin mirar el ajuste (V20 solo lo VALIDA cuando no hay orden de volumen).
        int minimumMinutes = context.prescription() == null ? 0
                : sessionMinutes * context.prescription().floorPct() / 100;
        int techo = profile.maxSessionMinutes();

        var workouts = new ArrayList<PlanDraft.DraftWorkout>();
        Set<Long> previousDayIds = Set.of();
        for (int i = 0; i < dates.size(); i++) {
            var focus = focuses.get(i);
            boolean consecutive = i > 0 && dates.get(i - 1).plusDays(1).equals(dates.get(i));
            // V18: nada del dia anterior si los dias estan pegados.
            selector.block(consecutive ? previousDayIds : Set.of());
            var picked = new ArrayList<>(selector.pick(focus, exercisesPerSession));

            var exercises = new ArrayList<PlanDraft.DraftExercise>();
            for (var candidate : picked) {
                exercises.add(toDraftExercise(candidate, prescription, context));
            }

            // V20: sin orden de volumen, la sesion llega al minimo de minutos
            // con trabajo, nunca alargando descansos. Orden (plan 29): ejercicios
            // con topes de grupos pequenos -> 3 series en grupos grandes -> 3
            // series en pequenos -> ejercicios sin topes -> 3 series en esos.
            if (minimumMinutes > 0 && context.prescription() != null) {
                var date = dates.get(i);
                java.util.function.BooleanSupplier falta = () -> durationEstimator.estimateMinutes(
                        new PlanDraft.DraftWorkout(date, focus, nameOf(focus), sessionMinutes, exercises),
                        context.prescription()) < minimumMinutes;

                addUntilMinimum(selector, focus, picked, exercises, prescription, context, falta, true);
                raiseSetsUntilMinimum(picked, exercises, falta, true);
                raiseSetsUntilMinimum(picked, exercises, falta, false);
                addUntilMinimum(selector, focus, picked, exercises, prescription, context, falta, false);
                raiseSetsUntilMinimum(picked, exercises, falta, false);
            }

            // V4/V17: si el contenido supera el techo del perfil, se quitan
            // ejercicios del final. Antes se declaraba el techo con un contenido
            // mayor y V17 lo rechazaba (fuerza, 4x6 con 120 s, en 30 minutos).
            if (context.prescription() != null) {
                while (exercises.size() > 2 && durationEstimator.estimateMinutes(new PlanDraft.DraftWorkout(
                                dates.get(i), focus, nameOf(focus), sessionMinutes, exercises),
                        context.prescription()) > techo) {
                    exercises.remove(exercises.size() - 1);
                }
            }
            previousDayIds = exercises.stream().map(PlanDraft.DraftExercise::exerciseId)
                    .collect(java.util.stream.Collectors.toSet());
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
        // V19: los ejercicios repetidos no superan su tope antes de reducir. El
        // plan base se arma sin mirar la semana anterior, asi que podia subir
        // series (al completar la duracion) o repeticiones (zona) de algo que
        // la persona no completo, y V19 dejaba la semana sin plan.
        var capped = applyReductionCaps(draft, context);
        var adjusted = new VolumeReducer(durationToRepsDivisor).apply(capped, context);
        return withRationale(withEstimatedDurations(adjusted, context), context);
    }

    // ------------------------------------------------------------ duracion (V20)

    private void addUntilMinimum(ExerciseSelector selector, WorkoutFocus focus,
                                 List<CandidateExercise> picked,
                                 List<PlanDraft.DraftExercise> exercises,
                                 TargetPrescription prescription, PlanGenerationContext context,
                                 java.util.function.BooleanSupplier falta, boolean respetarTopes) {
        while (exercises.size() < MAX_EXERCISES_PER_SESSION && falta.getAsBoolean()) {
            var extra = selector.pickAdditional(focus, picked, respetarTopes);
            if (extra.isEmpty()) return;
            picked.add(extra.get());
            exercises.add(toDraftExercise(extra.get(), prescription, context));
        }
    }

    /**
     * Sube a {@value #FILL_MAX_SETS} series, de a un ejercicio y en orden, hasta
     * llegar al minimo. picked y exercises van en paralelo (mismo indice).
     */
    private void raiseSetsUntilMinimum(List<CandidateExercise> picked,
                                       List<PlanDraft.DraftExercise> exercises,
                                       java.util.function.BooleanSupplier falta, boolean soloGrandes) {
        for (int k = 0; k < exercises.size() && falta.getAsBoolean(); k++) {
            if (soloGrandes && !ExerciseSelector.isMajor(picked.get(k).bodyPartCode())) continue;
            var e = exercises.get(k);
            if (e.prescriptionType() == PrescriptionType.SETS_REPS && e.plannedSets() != null
                    && e.plannedSets() < FILL_MAX_SETS)
                exercises.set(k, new PlanDraft.DraftExercise(e.exerciseId(), e.prescriptionType(),
                        FILL_MAX_SETS, e.plannedReps(), e.plannedDurationSeconds(), e.targetLoadKg(),
                        e.restSeconds(), e.notes()));
        }
    }

    // ------------------------------------------------------------ reduccion (V19)

    /**
     * Baja series y repeticiones de los ejercicios repetidos hasta su tope de
     * V19 (PlanDraftValidator.reductionCaps). Sin REDUCE_VOLUME no hay topes y
     * devuelve el borrador tal cual. Solo baja: nunca sube nada.
     */
    private PlanDraft applyReductionCaps(PlanDraft draft, PlanGenerationContext context) {
        var caps = main.web.services.fitsense.planning.domain.services.PlanDraftValidator.reductionCaps(context);
        if (caps.isEmpty()) return draft;

        var workouts = draft.workouts().stream()
                .map(workout -> new PlanDraft.DraftWorkout(workout.scheduledDate(), workout.focus(),
                        workout.name(), workout.expectedDurationMinutes(),
                        workout.exercises().stream().map(e -> {
                            var cap = caps.get(e.exerciseId());
                            if (cap == null || e.prescriptionType() != PrescriptionType.SETS_REPS
                                    || e.plannedSets() == null || e.plannedReps() == null) return e;
                            short sets = (short) Math.min(e.plannedSets(), cap.maxSets());
                            short reps = (short) Math.min(e.plannedReps(), cap.maxReps());
                            if (sets == e.plannedSets() && reps == e.plannedReps()) return e;
                            return new PlanDraft.DraftExercise(e.exerciseId(), e.prescriptionType(),
                                    sets, reps, e.plannedDurationSeconds(), e.targetLoadKg(),
                                    e.restSeconds(), e.notes());
                        }).toList()))
                .toList();
        return new PlanDraft(draft.source(), draft.modelName(), draft.planName(),
                draft.declaredTotalVolume(), draft.rationale(), workouts);
    }

    // ------------------------------------------------------------------- 20.4

    private PlanDraft.DraftExercise toDraftExercise(CandidateExercise candidate,
                                                    TargetPrescription prescription,
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

        // Minimo por zona (migracion V20): la tabla por objetivo pone 6
        // repeticiones para fuerza, y 6 elevaciones de talon no son coherentes.
        var limites = context.prescription() == null ? null : context.prescription().repLimits();
        String level = context.profile().fitnessLevel();
        // Zona pequena (gemelos, abdomen): unas repeticiones mas que la base
        // (principio 2). Sin esto todo el plan 29 salio a 12.
        int base = prescription.reps()
                + (SMALL_ZONES.contains(candidate.bodyPartCode()) ? SMALL_ZONE_EXTRA_REPS : 0);
        int maximo = limites == null || limites.maxReps() == null ? Integer.MAX_VALUE : limites.maxReps();
        short reps = limites == null ? (short) base
                : (short) Math.min(maximo,
                Math.max(base, limites.minRepsFor(candidate.bodyPartCode(), level)));
        // Principio 5: principiante, 2 series.
        short sets = prescription.setsFor(level);

        return new PlanDraft.DraftExercise(candidate.exerciseId(), PrescriptionType.SETS_REPS,
                sets, reps, null, suggestedLoad,
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