package main.web.services.fitsense.planning.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.valueobjects.PrescriptionType;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutFocus;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un entrenamiento indicado para un dia concreto. Entidad dentro del agregado
 * WeeklyTrainingPlan, no agregado propio: la unidad de consistencia es la
 * version de la semana.
 */
@Getter
@Entity
@Table(name = "planned_workouts")
public class PlannedWorkout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planned_workout_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private WeeklyTrainingPlan plan;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "focus_code", nullable = false, length = 30)
    private WorkoutFocus focusCode;

    @Column(name = "workout_name", nullable = false, length = 150)
    private String workoutName;

    /** Peso de esta sesion en la adherencia ponderada. */
    @Column(name = "expected_duration_minutes", nullable = false)
    private Short expectedDurationMinutes;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkoutStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 40)
    private SkipReason skipReason;

    /** 23:59 hora local del dia programado. Define que entrenamientos son exigibles. */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "plannedWorkout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("exerciseOrder ASC")
    private List<PlannedWorkoutExercise> exercises = new ArrayList<>();

    protected PlannedWorkout() {
        // JPA
    }

    public PlannedWorkout(WeeklyTrainingPlan plan, LocalDate scheduledDate, WorkoutFocus focusCode,
                   String workoutName, int expectedDurationMinutes, short displayOrder,
                   OffsetDateTime expiresAt) {
        this.plan = plan;
        this.scheduledDate = scheduledDate;
        this.focusCode = focusCode;
        this.workoutName = workoutName;
        this.expectedDurationMinutes = (short) expectedDurationMinutes;
        this.displayOrder = displayOrder;
        this.expiresAt = expiresAt;
        this.status = WorkoutStatus.SCHEDULED;
    }

    public PlannedWorkoutExercise addExercise(Long exerciseId, PrescriptionType prescriptionType,
                                              Short plannedSets, Short plannedReps,
                                              Integer plannedDurationSeconds, BigDecimal targetLoadKg,
                                              Short restSeconds, String notes) {
        var order = (short) (exercises.size() + 1);
        var exercise = new PlannedWorkoutExercise(this, exerciseId, order, prescriptionType,
                plannedSets, plannedReps, plannedDurationSeconds, targetLoadKg, restSeconds, notes);
        exercises.add(exercise);
        return exercise;
    }

    public List<PlannedWorkoutExercise> exercisesView() {
        return Collections.unmodifiableList(exercises);
    }

    // ------------------------------------------------------------ transiciones

    public void markInProgress() {
        if (status.isFinished())
            throw new DomainRuleViolationException(
                    "Este entrenamiento ya se cerro como %s.".formatted(status));
        this.status = WorkoutStatus.IN_PROGRESS;
    }

    /**
     * El resultado lo determina execution al cerrar la sesion. Planning solo
     * refleja el desenlace: no calcula porcentajes ni conoce las series reales.
     */
    public void markOutcome(WorkoutStatus outcome, SkipReason reason) {
        if (outcome == null || !outcome.isFinished())
            throw new DomainRuleViolationException(
                    "El desenlace debe ser COMPLETED, PARTIAL o SKIPPED.");
        this.status = outcome;
        this.skipReason = outcome == WorkoutStatus.SKIPPED ? reason : null;
    }

    public void markSkipped(SkipReason reason) {
        markOutcome(WorkoutStatus.SKIPPED, reason);
    }

    public void markReplaced() {
        if (!status.isFinished()) this.status = WorkoutStatus.REPLACED;
    }

    // --------------------------------------------------------------- consultas

    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(expiresAt);
    }

    /** Exigible: llego su fecha o ya vencio, y nadie lo cerro todavia. */
    public boolean isPending(OffsetDateTime now) {
        return status == WorkoutStatus.SCHEDULED || status == WorkoutStatus.IN_PROGRESS;
    }

    public int assignedExerciseCount() {
        return exercises.size();
    }

    public int equivalentVolume(int durationToRepsDivisor) {
        return exercises.stream()
                .mapToInt(exercise -> exercise.equivalentVolume(durationToRepsDivisor))
                .sum();
    }
}
