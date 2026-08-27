package main.web.services.fitsense.analytics.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.analytics.domain.model.valueobjects.MetricsCalculation;
import main.web.services.fitsense.analytics.domain.model.valueobjects.RiskLevel;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Cierre semanal. Es la tabla que se exporta para el analisis de la tesis.
 * <p>
 * Se recalcula, no se acumula: si una semana se vuelve a cerrar, se sobrescribe
 * con la misma version de configuracion. La restriccion uq_wum_user_week
 * garantiza una fila por participante y semana.
 */
@Getter
@Entity
@Table(name = "weekly_user_metrics")
public class WeeklyUserMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_metric_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "week_number", nullable = false)
    private Short weekNumber;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "has_active_plan", nullable = false)
    private boolean hasActivePlan;

    @Column(name = "scheduled_workouts", nullable = false)
    private Short scheduledWorkouts;

    @Column(name = "valid_workouts", nullable = false)
    private Short validWorkouts;

    @Column(name = "completed_workouts", nullable = false)
    private Short completedWorkouts;

    @Column(name = "skipped_workouts", nullable = false)
    private Short skippedWorkouts;

    @Column(name = "assigned_exercises", nullable = false)
    private Short assignedExercises;

    @Column(name = "completed_exercises", nullable = false)
    private Short completedExercises;

    /** Metrica primaria del estudio. NULL si la semana no llego a generarse. */
    @Column(name = "weighted_adherence_pct", precision = 5, scale = 2)
    private BigDecimal weightedAdherencePct;

    @Column(name = "frequency_adherence_pct", precision = 5, scale = 2)
    private BigDecimal frequencyAdherencePct;

    @Column(name = "workout_adherence_pct", precision = 5, scale = 2)
    private BigDecimal workoutAdherencePct;

    @Column(name = "exercise_adherence_pct", precision = 5, scale = 2)
    private BigDecimal exerciseAdherencePct;

    @Column(name = "total_training_minutes", nullable = false)
    private Integer totalTrainingMinutes;

    @Column(name = "average_session_rpe", precision = 4, scale = 2)
    private BigDecimal averageSessionRpe;

    @Column(name = "average_satisfaction", precision = 4, scale = 2)
    private BigDecimal averageSatisfaction;

    @Column(name = "consecutive_skips", nullable = false)
    private Short consecutiveSkips;

    @Column(name = "days_since_last_workout")
    private Short daysSinceLastWorkout;

    @Enumerated(EnumType.STRING)
    @Column(name = "dominant_skip_reason", length = 40)
    private SkipReason dominantSkipReason;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private String riskFactors;

    @Column(name = "is_dropout", nullable = false)
    private boolean dropout;

    /** Con que version de umbrales se produjo esta fila. Sin esto no es comparable. */
    @Column(name = "calculation_version", nullable = false, length = 30)
    private String calculationVersion;

    @Column(name = "calculated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime calculatedAt;

    protected WeeklyUserMetrics() {
        // JPA
    }

    public WeeklyUserMetrics(Long userId, LocalDate weekStartDate, LocalDate weekEndDate,
                             MetricsCalculation calculation, String riskFactorsJson,
                             String calculationVersion) {
        this.userId = userId;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.calculationVersion = calculationVersion;
        apply(calculation, riskFactorsJson);
    }

    /** Recalculo de una semana ya cerrada: sobrescribe en vez de crear una fila nueva. */
    public void recalculate(MetricsCalculation calculation, String riskFactorsJson,
                            String calculationVersion) {
        this.calculationVersion = calculationVersion;
        apply(calculation, riskFactorsJson);
    }

    private void apply(MetricsCalculation calculation, String riskFactorsJson) {
        this.planId = calculation.planId();
        this.weekNumber = calculation.weekNumber();
        this.hasActivePlan = calculation.hasActivePlan();
        this.scheduledWorkouts = (short) calculation.scheduledWorkouts();
        this.validWorkouts = (short) calculation.validWorkouts();
        this.completedWorkouts = (short) calculation.completedWorkouts();
        this.skippedWorkouts = (short) calculation.skippedWorkouts();
        this.assignedExercises = (short) calculation.assignedExercises();
        this.completedExercises = (short) calculation.completedExercises();
        this.weightedAdherencePct = calculation.weightedAdherencePct();
        this.frequencyAdherencePct = calculation.frequencyAdherencePct();
        this.workoutAdherencePct = calculation.workoutAdherencePct();
        this.exerciseAdherencePct = calculation.exerciseAdherencePct();
        this.totalTrainingMinutes = calculation.totalTrainingMinutes();
        this.averageSessionRpe = calculation.averageSessionRpe();
        this.averageSatisfaction = calculation.averageSatisfaction();
        this.consecutiveSkips = (short) calculation.consecutiveSkips();
        this.daysSinceLastWorkout = calculation.daysSinceLastWorkout();
        this.dominantSkipReason = calculation.dominantSkipReason() == null
                ? null : SkipReason.valueOf(calculation.dominantSkipReason());
        this.riskScore = calculation.riskScore();
        this.riskLevel = calculation.riskLevel();
        this.riskFactors = riskFactorsJson;
        this.dropout = calculation.dropout();
    }

    public boolean isMeasurable() {
        return hasActivePlan && weightedAdherencePct != null;
    }
}
