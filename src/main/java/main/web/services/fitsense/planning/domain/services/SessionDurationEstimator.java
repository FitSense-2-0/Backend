package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.configuration.domain.model.valueobjects.PrescriptionParams;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanDraft;
import main.web.services.fitsense.planning.domain.model.valueobjects.PrescriptionType;
import org.springframework.stereotype.Service;

/**
 * Cuanto dura de verdad una sesion, a partir de su contenido.
 * <pre>
 *   segundos = calentamiento
 *            + Σ_ejercicio [ trabajo + (series - 1) x descanso ]
 *            + (numero de ejercicios - 1) x transicion
 *
 *   trabajo SETS_REPS = series x repeticiones x segundos_por_repeticion
 *   trabajo DURATION  = series x segundos_prescritos
 * </pre>
 * Servicio de dominio puro: no consulta nada y con los mismos parametros da
 * siempre el mismo numero. Esa reproducibilidad es lo que permite recalcular la
 * duracion de un plan de hace meses y comparar.
 * <p>
 * Es una ESTIMACION, no una medida. No modela la fatiga, ni el tiempo de
 * espera por una maquina ocupada, ni las diferencias entre participantes. Sirve
 * para detectar declaraciones incoherentes con el contenido, que es justo lo
 * que la validacion 17 necesita, y asi debe declararse en la memoria.
 * <p>
 * Lo usan los dos generadores: el motor de reglas lo aplica para DECLARAR su
 * duracion, y el validador para COMPROBAR la que declaro la IA. Que ambos usen
 * la misma formula es lo que evita medir a los dos brazos del estudio con
 * varas distintas.
 */
@Service
public class SessionDurationEstimator {

    /** Minutos que dura de verdad el contenido de un entrenamiento. */
    public int estimateMinutes(PlanDraft.DraftWorkout workout, PrescriptionParams params) {
        return estimateMinutes(workout.exercises().size(),
                totalSeconds(workout, params), params);
    }

    private int estimateMinutes(int exerciseCount, long workAndRestSeconds,
                                PrescriptionParams params) {
        if (exerciseCount <= 0) return 0;

        long transitions = (long) (exerciseCount - 1) * params.transitionSecondsOrDefault();
        long warmup = (long) params.warmupMinutesOrDefault() * 60;
        long total = warmup + workAndRestSeconds + transitions;

        return (int) Math.max(1, Math.round(total / 60.0));
    }

    private long totalSeconds(PlanDraft.DraftWorkout workout, PrescriptionParams params) {
        long seconds = 0;
        for (var exercise : workout.exercises()) {
            seconds += workSeconds(exercise, params) + restSeconds(exercise, params);
        }
        return seconds;
    }

    private long workSeconds(PlanDraft.DraftExercise exercise, PrescriptionParams params) {
        int sets = exercise.plannedSets() == null ? 1 : exercise.plannedSets();

        if (exercise.prescriptionType() == PrescriptionType.DURATION) {
            int seconds = exercise.plannedDurationSeconds() == null
                    ? 0 : exercise.plannedDurationSeconds();
            return (long) sets * seconds;
        }

        int reps = exercise.plannedReps() == null ? 0 : exercise.plannedReps();
        return (long) sets * reps * params.secondsPerRepOrDefault();
    }

    /** Entre series, no despues de la ultima: de ahi el menos uno. */
    private long restSeconds(PlanDraft.DraftExercise exercise, PrescriptionParams params) {
        int sets = exercise.plannedSets() == null ? 1 : exercise.plannedSets();
        int rest = exercise.restSeconds() == null
                ? params.defaultRestSecondsOrDefault() : exercise.restSeconds();
        return (long) Math.max(0, sets - 1) * rest;
    }
}