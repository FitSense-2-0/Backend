package main.web.services.fitsense.planning.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.valueobjects.GenerationSource;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanStatus;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutFocus;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.model.aggregates.CreatedAuditableAbstractAggregateRoot;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Lo que el sistema indico para una semana. Es inmutable en su contenido: un
 * ajuste no edita el plan, crea la version siguiente y marca esta como
 * REPLACED. Sin esa regla la adherencia de una semana dejaria de tener un
 * denominador estable y el estudio no podria interpretarse.
 * <p>
 * planned_workouts y planned_workout_exercises son entidades de este agregado,
 * no agregados propios: la version es la unidad de consistencia.
 */
@Getter
@Entity
@Table(name = "weekly_training_plans")
public class WeeklyTrainingPlan
        extends CreatedAuditableAbstractAggregateRoot<WeeklyTrainingPlan> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "week_number", nullable = false)
    private Short weekNumber;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "plan_version", nullable = false)
    private Short planVersion;

    /** Version anterior que esta reemplaza. Escalar, no relacion: es una cadena, no un arbol navegable. */
    @Column(name = "parent_plan_id")
    private Long parentPlanId;

    @Column(name = "planned_days_count", nullable = false)
    private Short plannedDaysCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_source", nullable = false, length = 20)
    private GenerationSource generationSource;

    @Column(name = "model_name", length = 100)
    private String modelName;

    /** Datos exactos enviados al generador. Es la evidencia reproducible. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_snapshot", columnDefinition = "jsonb")
    private String outputSnapshot;

    @Column(name = "adjustment_applied", length = 40)
    private String adjustmentApplied;

    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    @Column(name = "generation_attempts", nullable = false)
    private Short generationAttempts;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "replaced_at")
    private OffsetDateTime replacedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<PlannedWorkout> workouts = new ArrayList<>();

    protected WeeklyTrainingPlan() {
        // JPA
    }

    private WeeklyTrainingPlan(Long userId, short weekNumber, LocalDate weekStartDate,
                               LocalDate weekEndDate, short planVersion, Long parentPlanId,
                               GenerationSource generationSource, String modelName,
                               String inputSnapshot, short generationAttempts) {
        if (userId == null)
            throw new DomainRuleViolationException("El plan necesita un usuario.");
        if (weekEndDate == null || weekStartDate == null || !weekEndDate.isAfter(weekStartDate))
            throw new DomainRuleViolationException("La semana debe terminar despues de empezar.");
        if (inputSnapshot == null || inputSnapshot.isBlank())
            throw new DomainRuleViolationException(
                    "Un plan sin input_snapshot no es reproducible y no puede guardarse.");

        this.userId = userId;
        this.weekNumber = weekNumber;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.planVersion = planVersion;
        this.parentPlanId = parentPlanId;
        this.generationSource = generationSource;
        this.modelName = modelName;
        this.inputSnapshot = inputSnapshot;
        this.generationAttempts = generationAttempts;
        this.plannedDaysCount = 0;
        this.status = PlanStatus.ACTIVE;
        this.activatedAt = OffsetDateTime.now();
    }

    /** Primera version de una semana. */
    public static WeeklyTrainingPlan firstVersion(Long userId, short weekNumber,
                                                  LocalDate weekStartDate, LocalDate weekEndDate,
                                                  GenerationSource source, String modelName,
                                                  String inputSnapshot, short attempts) {
        return new WeeklyTrainingPlan(userId, weekNumber, weekStartDate, weekEndDate,
                (short) 1, null, source, modelName, inputSnapshot, attempts);
    }

    /**
     * Version siguiente de una semana ya generada. La razon del ajuste es
     * obligatoria: un cambio de plan sin motivo registrado no es evidencia de
     * adaptacion, es ruido.
     */
    public static WeeklyTrainingPlan nextVersionOf(WeeklyTrainingPlan previous,
                                                   GenerationSource source, String modelName,
                                                   String inputSnapshot, short attempts,
                                                   String adjustmentApplied, String adjustmentReason) {
        if (previous == null)
            throw new DomainRuleViolationException("No hay version previa que reemplazar.");
        if (adjustmentReason == null || adjustmentReason.isBlank())
            throw new DomainRuleViolationException("Un plan ajustado debe declarar el motivo del ajuste.");

        var next = new WeeklyTrainingPlan(previous.userId, previous.weekNumber,
                previous.weekStartDate, previous.weekEndDate,
                (short) (previous.planVersion + 1), previous.id,
                source, modelName, inputSnapshot, attempts);
        next.adjustmentApplied = adjustmentApplied;
        next.adjustmentReason = adjustmentReason;
        return next;
    }

    // ------------------------------------------------------------ construccion

    public PlannedWorkout addWorkout(LocalDate scheduledDate, WorkoutFocus focus, String name,
                                     int expectedDurationMinutes, OffsetDateTime expiresAt) {
        if (scheduledDate.isBefore(weekStartDate) || scheduledDate.isAfter(weekEndDate))
            throw new DomainRuleViolationException(
                    "El dia %s cae fuera de la semana del plan.".formatted(scheduledDate));

        var order = (short) (workouts.size() + 1);
        var workout = new PlannedWorkout(this, scheduledDate, focus, name,
                expectedDurationMinutes, order, expiresAt);
        workouts.add(workout);
        this.plannedDaysCount = (short) workouts.size();
        return workout;
    }

    public void attachOutputSnapshot(String outputSnapshot) {
        this.outputSnapshot = outputSnapshot;
    }

    public void recordAdjustment(String adjustmentApplied, String adjustmentReason) {
        this.adjustmentApplied = adjustmentApplied;
        this.adjustmentReason = adjustmentReason;
    }

    // ------------------------------------------------------------ transiciones

    /** La sustituye una version posterior. Los entrenamientos aun abiertos quedan REPLACED. */
    public void markReplaced() {
        if (status == PlanStatus.REPLACED) return;
        this.status = PlanStatus.REPLACED;
        this.replacedAt = OffsetDateTime.now();
        workouts.forEach(PlannedWorkout::markReplaced);
    }

    /** La semana termino sin reemplazo. */
    public void markCompleted() {
        if (status == PlanStatus.ACTIVE) this.status = PlanStatus.COMPLETED;
    }

    // --------------------------------------------------------------- consultas

    public List<PlannedWorkout> workoutsView() {
        return Collections.unmodifiableList(workouts);
    }

    public Optional<PlannedWorkout> findWorkout(Long plannedWorkoutId) {
        return workouts.stream().filter(w -> w.getId().equals(plannedWorkoutId)).findFirst();
    }

    public boolean isActive() {
        return status == PlanStatus.ACTIVE;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(weekStartDate) && !date.isAfter(weekEndDate);
    }

    public int totalExpectedMinutes() {
        return workouts.stream().mapToInt(w -> w.getExpectedDurationMinutes()).sum();
    }

    public int assignedExerciseCount() {
        return workouts.stream().mapToInt(PlannedWorkout::assignedExerciseCount).sum();
    }

    /** Volumen total en repeticiones equivalentes. Base de comparacion de la intervencion. */
    public int equivalentVolume(int durationToRepsDivisor) {
        return workouts.stream()
                .mapToInt(w -> w.equivalentVolume(durationToRepsDivisor))
                .sum();
    }
}
