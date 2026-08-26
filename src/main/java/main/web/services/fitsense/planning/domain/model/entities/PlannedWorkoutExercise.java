package main.web.services.fitsense.planning.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.planning.domain.model.valueobjects.PrescriptionType;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lo que el sistema indico para un ejercicio. Nunca se escribe aqui el
 * resultado real: eso vive en workout_session_exercises.
 * <p>
 * exercise_id es un escalar, no una relacion JPA: el catalogo es otro contexto
 * y planning no debe navegar a su agregado. La integridad la garantiza la FK.
 */
@Getter
@Entity
@Table(name = "planned_workout_exercises")
public class PlannedWorkoutExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planned_exercise_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_workout_id", nullable = false)
    private PlannedWorkout plannedWorkout;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "exercise_order", nullable = false)
    private Short exerciseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "prescription_type", nullable = false, length = 25)
    private PrescriptionType prescriptionType;

    @Column(name = "planned_sets")
    private Short plannedSets;

    @Column(name = "planned_reps")
    private Short plannedReps;

    @Column(name = "planned_duration_seconds")
    private Integer plannedDurationSeconds;

    @Column(name = "target_load_kg", precision = 7, scale = 2)
    private BigDecimal targetLoadKg;

    @Column(name = "rest_seconds")
    private Short restSeconds;

    @Column(name = "notes", length = 300)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PlannedWorkoutExercise() {
        // JPA
    }

    PlannedWorkoutExercise(PlannedWorkout plannedWorkout, Long exerciseId, short exerciseOrder,
                           PrescriptionType prescriptionType, Short plannedSets, Short plannedReps,
                           Integer plannedDurationSeconds, BigDecimal targetLoadKg,
                           Short restSeconds, String notes) {
        this.plannedWorkout = plannedWorkout;
        this.exerciseId = requireId(exerciseId);
        this.exerciseOrder = exerciseOrder;
        this.prescriptionType = requirePrescription(prescriptionType);
        this.plannedSets = plannedSets;
        this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.targetLoadKg = targetLoadKg;
        this.restSeconds = restSeconds;
        this.notes = notes;
        requireCoherence();
    }

    /**
     * Volumen semanal segun 18.1: series x repeticiones, o segundos / 30 para
     * los de duracion. El divisor es una convencion declarada, no una medida
     * fisiologica. La carga NO entra: es intensidad, no cantidad de trabajo.
     */
    public int equivalentVolume(int durationToRepsDivisor) {
        if (prescriptionType == PrescriptionType.SETS_REPS)
            return (plannedSets == null || plannedReps == null) ? 0 : plannedSets * plannedReps;
        if (plannedDurationSeconds == null || durationToRepsDivisor <= 0) return 0;
        int sets = plannedSets == null ? 1 : plannedSets;
        return Math.round((float) (plannedDurationSeconds * sets) / durationToRepsDivisor);
    }

    private void requireCoherence() {
        if (prescriptionType == PrescriptionType.SETS_REPS) {
            if (plannedSets == null || plannedReps == null)
                throw new DomainRuleViolationException(
                        "Una prescripcion SETS_REPS necesita series y repeticiones.");
        } else if (plannedDurationSeconds == null) {
            throw new DomainRuleViolationException(
                    "Una prescripcion DURATION necesita duracion en segundos.");
        }
    }

    private static Long requireId(Long exerciseId) {
        if (exerciseId == null)
            throw new DomainRuleViolationException("El ejercicio indicado no tiene identificador.");
        return exerciseId;
    }

    private static PrescriptionType requirePrescription(PrescriptionType type) {
        if (type == null)
            throw new DomainRuleViolationException("Falta el tipo de prescripcion del ejercicio.");
        return type;
    }
}
