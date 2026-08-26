package main.web.services.fitsense.planning.interfaces.acl;

/** Lo indicado para un ejercicio. Es el denominador contra el que execution compara. */
public record PlannedExerciseTarget(
        Long plannedExerciseId,
        Long exerciseId,
        String prescriptionType,
        Short plannedSets,
        Short plannedReps,
        Integer plannedDurationSeconds
) {
    /** Repeticiones totales indicadas. Se compara directo contra actual_reps_total. */
    public int plannedRepsTotal() {
        if (plannedSets == null || plannedReps == null) return 0;
        return plannedSets * plannedReps;
    }
}
