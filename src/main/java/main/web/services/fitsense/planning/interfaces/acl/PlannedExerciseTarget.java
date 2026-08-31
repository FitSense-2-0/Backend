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

    /**
     * Segundos totales indicados: series x duracion, igual que plannedRepsTotal
     * multiplica series por repeticiones.
     * <p>
     * Usar solo plannedDurationSeconds tomaria UNA serie como el total, y
     * cualquier ejercicio de duracion marcaria 100 % con un tercio del trabajo.
     * Inflaba la adherencia de forma sistematica.
     */
    public int plannedDurationTotal() {
        if (plannedDurationSeconds == null) return 0;
        return (plannedSets == null ? 1 : plannedSets) * plannedDurationSeconds;
    }
}