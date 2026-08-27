package main.web.services.fitsense.adaptation.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.adaptation.domain.model.valueobjects.AdjustmentDecision;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Evidencia de que el sistema se adapta. Una fila por participante y semana,
 * incluso cuando el ajuste es NONE: sin las filas de "no se ajusto" el analisis
 * no tendria grupo de comparacion dentro del propio participante.
 * <p>
 * Guarda por separado lo que la regla ORDENO (target_volume_change_pct) y lo que
 * el generador ENTREGO (actual_volume_change_pct). Comparar ambos es lo que
 * permite afirmar si el modelo obedece la restriccion, que es una pregunta
 * distinta de si el ajuste funciona.
 */
@Getter
@Entity
@Table(name = "user_interventions")
public class UserIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "intervention_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** uq_ui_metric: un solo ajuste por semana medida. */
    @Column(name = "weekly_metric_id", nullable = false)
    private Long weeklyMetricId;

    @Column(name = "source_plan_id", nullable = false)
    private Long sourcePlanId;

    @Column(name = "resulting_plan_id")
    private Long resultingPlanId;

    @Column(name = "trigger_adherence_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal triggerAdherencePct;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_skip_reason", length = 40)
    private SkipReason triggerSkipReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "adjustment_types", nullable = false, columnDefinition = "varchar(40)[]")
    private String[] adjustmentTypes;

    @Column(name = "target_volume_change_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetVolumeChangePct;

    @Column(name = "previous_week_volume", nullable = false)
    private Integer previousWeekVolume;

    @Column(name = "resulting_week_volume")
    private Integer resultingWeekVolume;

    @Column(name = "actual_volume_change_pct", precision = 6, scale = 2)
    private BigDecimal actualVolumeChangePct;

    @Column(name = "avg_sets_change", precision = 4, scale = 2)
    private BigDecimal avgSetsChange;

    @Column(name = "avg_reps_change", precision = 4, scale = 2)
    private BigDecimal avgRepsChange;

    @Column(name = "exercises_change")
    private Short exercisesChange;

    @Column(name = "duration_change_pct", precision = 6, scale = 2)
    private BigDecimal durationChangePct;

    @Column(name = "days_change")
    private Short daysChange;

    @Column(name = "difficulty_change")
    private Short difficultyChange;

    @Column(name = "load_change_pct", precision = 6, scale = 2)
    private BigDecimal loadChangePct;

    @Column(name = "message_shown", nullable = false, length = 500)
    private String messageShown;

    @Column(name = "rule_version", nullable = false, length = 30)
    private String ruleVersion;

    @Column(name = "applied_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime appliedAt;

    /** Se completa una semana despues: mide si el ajuste sirvio. */
    @Column(name = "adherence_after_pct", precision = 5, scale = 2)
    private BigDecimal adherenceAfterPct;

    @Column(name = "outcome", length = 20)
    private String outcome;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserIntervention() {
        // JPA
    }

    public UserIntervention(Long userId, Long weeklyMetricId, Long sourcePlanId,
                            BigDecimal triggerAdherencePct, SkipReason triggerSkipReason,
                            AdjustmentDecision decision, int previousWeekVolume,
                            int previousDaysPerWeek, int previousSessionMinutes,
                            int previousMaxDifficulty, String ruleVersion) {
        if (triggerAdherencePct == null)
            throw new DomainRuleViolationException(
                    "No se puede registrar una intervencion sin la adherencia que la disparo.");
        if (decision.types().isEmpty())
            throw new DomainRuleViolationException("La decision debe declarar al menos un tipo de ajuste.");

        this.userId = userId;
        this.weeklyMetricId = weeklyMetricId;
        this.sourcePlanId = sourcePlanId;
        this.triggerAdherencePct = triggerAdherencePct;
        this.triggerSkipReason = triggerSkipReason;
        this.adjustmentTypes = decision.typeNames().toArray(String[]::new);
        this.targetVolumeChangePct = BigDecimal.valueOf(decision.targetVolumeChangePct())
                .setScale(2, RoundingMode.HALF_UP);
        this.previousWeekVolume = previousWeekVolume;
        this.loadChangePct = BigDecimal.valueOf(decision.loadChangePct()).setScale(2, RoundingMode.HALF_UP);
        this.messageShown = decision.message();
        this.ruleVersion = ruleVersion;
        this.outcome = "PENDING";

        // Los deltas se guardan al decidir, no al generar: describen la ORDEN.
        // Lo que el generador entrego se mide aparte, en linkResultingPlan.
        if (decision.forcedDaysPerWeek() != null)
            this.daysChange = (short) (decision.forcedDaysPerWeek() - previousDaysPerWeek);
        if (decision.forcedMaxDifficulty() != null)
            this.difficultyChange = (short) (decision.forcedMaxDifficulty() - previousMaxDifficulty);
        if (decision.forcedSessionMinutes() != null && previousSessionMinutes > 0)
            this.durationChangePct = BigDecimal
                    .valueOf(decision.forcedSessionMinutes() - previousSessionMinutes)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(previousSessionMinutes), 2, RoundingMode.HALF_UP);
    }

    /**
     * Cierra el circulo: cuanto volumen entrego realmente el generador. Sin esto
     * la fila registra una orden sin comprobar si se cumplio.
     */
    public void linkResultingPlan(Long resultingPlanId, int resultingWeekVolume) {
        this.resultingPlanId = resultingPlanId;
        this.resultingWeekVolume = resultingWeekVolume;

        if (previousWeekVolume != null && previousWeekVolume > 0) {
            var change = BigDecimal.valueOf(resultingWeekVolume - previousWeekVolume)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(previousWeekVolume), 2, RoundingMode.HALF_UP);
            this.actualVolumeChangePct = change;
        }
    }

    /** Se llama al cerrar la semana siguiente: dice si el ajuste mejoro la adherencia. */
    public void recordOutcome(BigDecimal adherenceAfterPct, double tolerancePct) {
        this.adherenceAfterPct = adherenceAfterPct;
        if (adherenceAfterPct == null || triggerAdherencePct == null) {
            this.outcome = "PENDING";
            return;
        }

        double delta = adherenceAfterPct.doubleValue() - triggerAdherencePct.doubleValue();
        if (delta > tolerancePct) this.outcome = "IMPROVED";
        else if (delta < -tolerancePct) this.outcome = "WORSENED";
        else this.outcome = "UNCHANGED";
    }

    public List<String> adjustmentTypesAsList() {
        return adjustmentTypes == null ? List.of() : Arrays.asList(adjustmentTypes);
    }
}
