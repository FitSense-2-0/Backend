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
        current = reduceReps(current, adjustment);
        if (isWithinTarget(current, adjustment)) return current;

        current = reduceSets(current, adjustment);
        if (isWithinTarget(current, adjustment)) return current;

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

    private PlanDraft reduceReps(PlanDraft draft, PlanAdjustment adjustment) {
        int target = adjustment.targetVolume();
        int current = draft.volume(durationToRepsDivisor);
        if (current <= target) return draft;

        double factor = (double) target / current;

        return mapExercises(draft, exercise -> {
            if (exercise.prescriptionType() != PrescriptionType.SETS_REPS
                    || exercise.plannedReps() == null) return exercise;

            short reps = (short) Math.max(MIN_REPS, Math.round(exercise.plannedReps() * factor));
            return withSetsReps(exercise, exercise.plannedSets(), reps);
        });
    }

    private PlanDraft reduceSets(PlanDraft draft, PlanAdjustment adjustment) {
        return mapExercises(draft, exercise -> {
            if (exercise.prescriptionType() != PrescriptionType.SETS_REPS
                    || exercise.plannedSets() == null) return exercise;

            short sets = (short) Math.max(MIN_SETS, exercise.plannedSets() - 1);
            return withSetsReps(exercise, sets, exercise.plannedReps());
        });
    }

    /**
     * Quita ejercicios de a uno por sesion hasta entrar en el rango o tocar el
     * piso de 2. Se quita el ultimo de la lista: el orden de seleccion pone
     * primero uno de cada parte corporal, asi que el ultimo es el mas
     * prescindible del enfoque.
     */
    private PlanDraft removeExercises(PlanDraft draft, PlanAdjustment adjustment) {
        var current = draft;
        for (int round = 0; round < 4; round++) {
            if (isWithinTarget(current, adjustment)) return current;

            var workouts = new ArrayList<PlanDraft.DraftWorkout>();
            boolean removedAny = false;

            for (var workout : current.workouts()) {
                if (workout.exercises().size() > MIN_EXERCISES) {
                    var trimmed = new ArrayList<>(workout.exercises());
                    trimmed.remove(trimmed.size() - 1);
                    workouts.add(new PlanDraft.DraftWorkout(workout.scheduledDate(),
                            workout.focus(), workout.name(),
                            workout.expectedDurationMinutes(), List.copyOf(trimmed)));
                    removedAny = true;
                } else {
                    workouts.add(workout);
                }
            }

            current = replaceWorkouts(current, workouts);
            if (!removedAny) break;
        }
        return current;
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
