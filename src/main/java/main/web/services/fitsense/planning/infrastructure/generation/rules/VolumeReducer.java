package main.web.services.fitsense.planning.infrastructure.generation.rules;

import main.web.services.fitsense.planning.domain.model.valueobjects.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplicacion de los ajustes segun 20.5. El reparto es fijo y sin criterio:
 * primero se bajan repeticiones, luego series, y solo al final se quita un
 * ejercicio.
 * <p>
 * Los tramos del diseno:
 * <pre>
 *   hasta -20 %      repeticiones, con piso en 6
 *   -20 % a -35 %    repeticiones al piso y una serie menos, con piso en 2
 *   mas de -35 %     ademas un ejercicio menos por sesion, con piso en 2
 * </pre>
 * Bajar repeticiones antes que series no es una preferencia de entrenamiento:
 * es lo que permite aterrizar en el rango. Quitar una serie de cuatro es un
 * salto del 25 %, que casi siempre se pasa del objetivo de -20 % con margen
 * de 5 puntos; bajar de 12 a 10 repeticiones cae dentro.
 */
class VolumeReducer {

    private static final short MIN_REPS = 6;
    private static final short MIN_SETS = 2;
    private static final int MIN_EXERCISES = 2;

    private final int durationToRepsDivisor;

    VolumeReducer(int durationToRepsDivisor) {
        this.durationToRepsDivisor = durationToRepsDivisor;
    }

    PlanDraft apply(PlanDraft draft, PlanGenerationContext context) {
        var adjustment = context.adjustment();
        if (adjustment == null || !adjustment.isActive()) return draft;

        var current = applyDuration(draft, adjustment, context);
        // Orden: primero SERIES (hasta 2), despues repeticiones (hasta el minimo
        // de zona y nivel), por ultimo quitar ejercicios. Antes eran primero las
        // repeticiones y todo terminaba en 3x6, incoherente para una
        // principiante; 2 series es ademas lo que pide el principio 5.
        current = reduceSets(current, adjustment);
        if (isWithinTarget(current, adjustment)) return current;
        // Por debajo del minimo, seguir recortando solo empeora: el reductor no
        // sabe subir volumen. Se entrega tal cual y V8 decide.
        if (current.volume(durationToRepsDivisor) < adjustment.targetVolumeMin()) return current;

        current = reduceReps(current, adjustment, context);
        if (isWithinTarget(current, adjustment)) return current;
        if (current.volume(durationToRepsDivisor) < adjustment.targetVolumeMin()) return current;

        return removeExercises(current, adjustment);
    }

    // ------------------------------------------------------------------ pasos

    /** REDUCE_DURATION recorta expected_duration_minutes en el mismo porcentaje. */
    private PlanDraft applyDuration(PlanDraft draft, PlanAdjustment adjustment,
                                    PlanGenerationContext context) {
        if (!adjustment.has(AdjustmentType.REDUCE_DURATION)) return draft;

        double factor = 1.0 + (adjustment.targetVolumeChangePct() / 100.0);
        int floorMinutes = adjustment.forcedSessionMinutes() != null
                ? adjustment.forcedSessionMinutes() : 20;

        var workouts = draft.workouts().stream()
                .map(workout -> new PlanDraft.DraftWorkout(
                        workout.scheduledDate(), workout.focus(), workout.name(),
                        Math.max(floorMinutes,
                                (int) Math.round(workout.expectedDurationMinutes() * factor)),
                        workout.exercises()))
                .toList();

        return replaceWorkouts(draft, workouts);
    }

    /**
     * Baja repeticiones DE A UNA, siempre en el ejercicio con mas margen sobre
     * su minimo, hasta quedar dentro del maximo de la banda.
     * <p>
     * Antes aplicaba un unico factor a todos (reps x objetivo/actual). Con
     * minimos por zona (gemelos no bajan de 12) el factor no alcanzaba, la
     * reduccion de series entraba a cortar TODAS las series a la vez y la
     * semana caia muy por debajo de la banda: 540 -> 308 con objetivo 432.
     * Paso a paso, el volumen baja en saltos del tamano de las series de un
     * ejercicio y se detiene en cuanto entra.
     */
    private PlanDraft reduceReps(PlanDraft draft, PlanAdjustment adjustment,
                                 PlanGenerationContext context) {
        var bodyParts = new java.util.HashMap<Long, String>();
        context.availableExercises().forEach(c -> bodyParts.put(c.exerciseId(), c.bodyPartCode()));
        var limites = context.prescription() == null ? null : context.prescription().repLimits();

        var workouts = mutableCopy(draft);
        while (volumeOf(draft, workouts) > adjustment.targetVolumeMax()) {
            int[] best = null;
            int bestMargin = 0;
            for (int w = 0; w < workouts.size(); w++) {
                for (int e = 0; e < workouts.get(w).size(); e++) {
                    var exercise = workouts.get(w).get(e);
                    if (exercise.prescriptionType() != PrescriptionType.SETS_REPS
                            || exercise.plannedReps() == null) continue;
                    // Nunca bajo el minimo de su zona (migracion V20).
                    int minimo = Math.max(MIN_REPS, limites == null ? MIN_REPS
                            : limites.minRepsFor(bodyParts.get(exercise.exerciseId()),
                            context.profile().fitnessLevel()));
                    int margin = exercise.plannedReps() - minimo;
                    if (margin > bestMargin) { bestMargin = margin; best = new int[]{w, e}; }
                }
            }
            if (best == null) break;
            var exercise = workouts.get(best[0]).get(best[1]);
            workouts.get(best[0]).set(best[1],
                    withSetsReps(exercise, exercise.plannedSets(), (short) (exercise.plannedReps() - 1)));
        }
        return rebuild(draft, workouts);
    }

    /** Igual que las repeticiones: una serie cada vez, en el ejercicio con mas series. */
    private PlanDraft reduceSets(PlanDraft draft, PlanAdjustment adjustment) {
        var workouts = mutableCopy(draft);
        while (volumeOf(draft, workouts) > adjustment.targetVolumeMax()) {
            int[] best = null;
            int bestSets = MIN_SETS;
            for (int w = 0; w < workouts.size(); w++) {
                for (int e = 0; e < workouts.get(w).size(); e++) {
                    var exercise = workouts.get(w).get(e);
                    if (exercise.prescriptionType() != PrescriptionType.SETS_REPS
                            || exercise.plannedSets() == null) continue;
                    if (exercise.plannedSets() > bestSets) { bestSets = exercise.plannedSets(); best = new int[]{w, e}; }
                }
            }
            if (best == null) break;
            var exercise = workouts.get(best[0]).get(best[1]);
            workouts.get(best[0]).set(best[1],
                    withSetsReps(exercise, (short) (exercise.plannedSets() - 1), exercise.plannedReps()));
        }
        return rebuild(draft, workouts);
    }

    private static List<List<PlanDraft.DraftExercise>> mutableCopy(PlanDraft draft) {
        var copy = new ArrayList<List<PlanDraft.DraftExercise>>();
        draft.workouts().forEach(workout -> copy.add(new ArrayList<>(workout.exercises())));
        return copy;
    }

    private int volumeOf(PlanDraft template, List<List<PlanDraft.DraftExercise>> workouts) {
        return rebuild(template, workouts).volume(durationToRepsDivisor);
    }

    private PlanDraft rebuild(PlanDraft template, List<List<PlanDraft.DraftExercise>> exercises) {
        var workouts = new ArrayList<PlanDraft.DraftWorkout>();
        for (int i = 0; i < template.workouts().size(); i++) {
            var workout = template.workouts().get(i);
            workouts.add(new PlanDraft.DraftWorkout(workout.scheduledDate(), workout.focus(), workout.name(),
                    workout.expectedDurationMinutes(), List.copyOf(exercises.get(i))));
        }
        return replaceWorkouts(template, workouts);
    }

    /**
     * Quita ejercicios de a uno por sesion hasta entrar en el rango o tocar el
     * piso de 2. Se quita el ultimo de la lista: el orden de seleccion pone
     * primero uno de cada parte corporal, asi que el ultimo es el mas
     * prescindible del enfoque.
     */
    private PlanDraft removeExercises(PlanDraft draft, PlanAdjustment adjustment) {
        // De a UN ejercicio, siempre de la sesion con mas ejercicios, y se para
        // en cuanto el volumen baja del maximo. Antes quitaba uno por sesion por
        // ronda y seguia aunque ya estuviera por debajo del minimo: una semana
        // con objetivo 266 terminaba en 144.
        var workouts = mutableCopy(draft);
        while (volumeOf(draft, workouts) > adjustment.targetVolumeMax()) {
            int target = -1;
            for (int w = 0; w < workouts.size(); w++) {
                if (workouts.get(w).size() <= MIN_EXERCISES) continue;
                if (target < 0 || workouts.get(w).size() > workouts.get(target).size()) target = w;
            }
            if (target < 0) break;
            workouts.get(target).remove(workouts.get(target).size() - 1);
        }
        return rebuild(draft, workouts);
    }

    // ---------------------------------------------------------------- helpers

    private boolean isWithinTarget(PlanDraft draft, PlanAdjustment adjustment) {
        int volume = draft.volume(durationToRepsDivisor);
        return volume >= adjustment.targetVolumeMin() && volume <= adjustment.targetVolumeMax();
    }

    private PlanDraft.DraftExercise withSetsReps(PlanDraft.DraftExercise exercise,
                                                 Short sets, Short reps) {
        return new PlanDraft.DraftExercise(exercise.exerciseId(), exercise.prescriptionType(),
                sets, reps, exercise.plannedDurationSeconds(), exercise.targetLoadKg(),
                exercise.restSeconds(), exercise.notes());
    }

    private PlanDraft mapExercises(PlanDraft draft,
                                   java.util.function.UnaryOperator<PlanDraft.DraftExercise> mapper) {
        var workouts = draft.workouts().stream()
                .map(workout -> new PlanDraft.DraftWorkout(
                        workout.scheduledDate(), workout.focus(), workout.name(),
                        workout.expectedDurationMinutes(),
                        workout.exercises().stream().map(mapper).toList()))
                .toList();
        return replaceWorkouts(draft, workouts);
    }

    private PlanDraft replaceWorkouts(PlanDraft draft, List<PlanDraft.DraftWorkout> workouts) {
        return new PlanDraft(draft.source(), draft.modelName(), draft.planName(),
                draft.declaredTotalVolume(), draft.rationale(), workouts);
    }
}