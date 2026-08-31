package main.web.services.fitsense.execution.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.valueobjects.CompletionThresholds;
import main.web.services.fitsense.execution.domain.model.valueobjects.ExerciseOutcome;
import main.web.services.fitsense.execution.domain.model.valueobjects.ExerciseTarget;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Lo que el usuario hizo en un ejercicio. Esta tabla contra
 * planned_workout_exercises es toda la adherencia: lo demas son agregaciones.
 * <p>
 * No hay tabla de series: se guarda la suma de repeticiones, que es lo que se
 * compara contra planned_sets x planned_reps. Registrar serie por serie
 * multiplicaria las filas sin cambiar ninguna metrica del estudio.
 */
@Getter
@Entity
@Table(name = "workout_session_exercises")
public class WorkoutSessionExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_exercise_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSession session;

    /** Escalar: planned_workout_exercises pertenece a otro contexto. */
    @Column(name = "planned_exercise_id", nullable = false)
    private Long plannedExerciseId;

    @Column(name = "actual_sets")
    private Short actualSets;

    @Column(name = "actual_reps_total")
    private Integer actualRepsTotal;

    @Column(name = "actual_duration_seconds")
    private Integer actualDurationSeconds;

    @Column(name = "actual_load_kg", precision = 7, scale = 2)
    private BigDecimal actualLoadKg;

    @Column(name = "completion_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExerciseOutcome status;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 40)
    private SkipReason skipReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WorkoutSessionExercise() {
        // JPA
    }

    public WorkoutSessionExercise(WorkoutSession session, Long plannedExerciseId) {
        this.session = session;
        this.plannedExerciseId = plannedExerciseId;
        this.completionPercentage = BigDecimal.ZERO;
        this.status = ExerciseOutcome.SKIPPED;
    }

    /**
     * Registra el resultado y recalcula el porcentaje contra lo indicado.
     * Idempotente: reenviar el mismo ejercicio sobrescribe, no acumula. La app
     * reintenta envios y sin esto una mala conexion inflaria la adherencia.
     */
    public void record(ExerciseTarget target, Short actualSets, Integer actualRepsTotal,
                Integer actualDurationSeconds, BigDecimal actualLoadKg,
                SkipReason skipReason, CompletionThresholds thresholds) {

        this.actualSets = actualSets;
        this.actualRepsTotal = actualRepsTotal;
        this.actualDurationSeconds = actualDurationSeconds;
        this.actualLoadKg = actualLoadKg;
        this.skipReason = skipReason;
        this.completionPercentage = computePercentage(target, thresholds);
        this.status = classify(thresholds);

        // La causa solo tiene sentido si efectivamente no se hizo.
        if (this.status == ExerciseOutcome.COMPLETED) this.skipReason = null;
    }

    private BigDecimal computePercentage(ExerciseTarget target, CompletionThresholds thresholds) {
        int denominator = target.denominator();
        if (denominator <= 0) return BigDecimal.ZERO;

        int achieved = target.durationBased()
                ? (actualDurationSeconds == null ? 0 : actualDurationSeconds)
                : (actualRepsTotal == null ? 0 : actualRepsTotal);

        var percentage = BigDecimal.valueOf(achieved)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);

        // Tope: superar lo indicado no compensa otro ejercicio no hecho.
        var cap = BigDecimal.valueOf(thresholds.completionCapPct());
        return percentage.min(cap).max(BigDecimal.ZERO);
    }

    private ExerciseOutcome classify(CompletionThresholds thresholds) {
        if (completionPercentage.compareTo(BigDecimal.ZERO) <= 0) return ExerciseOutcome.SKIPPED;
        if (completionPercentage.doubleValue() >= thresholds.exerciseCompletedThresholdPct())
            return ExerciseOutcome.COMPLETED;
        return ExerciseOutcome.PARTIAL;
    }

    public boolean isCompleted() {
        return status == ExerciseOutcome.COMPLETED;
    }
}
