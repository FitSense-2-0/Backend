package main.web.services.fitsense.execution.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.execution.domain.model.entities.WorkoutSessionExercise;
import main.web.services.fitsense.execution.domain.model.valueobjects.*;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Lo que el usuario hizo. Vive separado de lo planificado a proposito: mezclar
 * lo indicado con lo ejecutado en una sola tabla haria imposible responder la
 * pregunta central del estudio, que es cuanto de lo indicado se cumplio.
 * <p>
 * planned_workout_id y plan_id son escalares: apuntan a otro contexto y la
 * integridad la garantizan las FK, no una relacion JPA navegable.
 */
@Getter
@Entity
@Table(name = "workout_sessions")
public class WorkoutSession extends AuditableAbstractAggregateRoot<WorkoutSession> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "planned_workout_id", nullable = false)
    private Long plannedWorkoutId;

    /** Version del plan vigente al iniciar la sesion. */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "attempt_number", nullable = false)
    private Short attemptNumber;

    /**
     * Regla del intento valido: el ultimo intento finalizado. Un indice unico
     * parcial impide que dos intentos del mismo entrenamiento cuenten a la vez.
     */
    @Column(name = "counts_toward_adherence", nullable = false)
    private boolean countsTowardAdherence;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "active_minutes")
    private Short activeMinutes;

    @Column(name = "completion_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercentage;

    @Column(name = "session_rpe")
    private Short sessionRpe;

    @Column(name = "satisfaction")
    private Short satisfaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SessionSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<WorkoutSessionExercise> exercises = new ArrayList<>();

    protected WorkoutSession() {
        // JPA
    }

    private WorkoutSession(Long userId, Long plannedWorkoutId, Long planId, short attemptNumber,
                           OffsetDateTime startedAt, SessionSource source, SessionStatus status) {
        this.userId = userId;
        this.plannedWorkoutId = plannedWorkoutId;
        this.planId = planId;
        this.attemptNumber = attemptNumber;
        this.startedAt = startedAt;
        this.source = source;
        this.status = status;
        this.completionPercentage = BigDecimal.ZERO;
        this.countsTowardAdherence = false;
    }

    /** Sesion en vivo: la app la abre al empezar el entrenamiento. */
    public static WorkoutSession start(Long userId, Long plannedWorkoutId, Long planId,
                                       short attemptNumber) {
        return new WorkoutSession(userId, plannedWorkoutId, planId, attemptNumber,
                OffsetDateTime.now(), SessionSource.APP_TRACKED, SessionStatus.IN_PROGRESS);
    }

    /**
     * Reporte retroactivo: el usuario declara que ya lo hizo. Nace finalizada,
     * porque no hay nada que seguir en vivo.
     */
    public static WorkoutSession reported(Long userId, Long plannedWorkoutId, Long planId,
                                          short attemptNumber, OffsetDateTime performedAt) {
        if (performedAt == null)
            throw new DomainRuleViolationException("Indica cuando hiciste el entrenamiento.");
        if (performedAt.isAfter(OffsetDateTime.now()))
            throw new DomainRuleViolationException("No puedes reportar un entrenamiento futuro.");

        return new WorkoutSession(userId, plannedWorkoutId, planId, attemptNumber,
                performedAt, SessionSource.USER_REPORTED, SessionStatus.IN_PROGRESS);
    }

    // ------------------------------------------------------------------ registro

    public void recordExercise(ExerciseTarget target, Short actualSets, Integer actualRepsTotal,
                               Integer actualDurationSeconds, BigDecimal actualLoadKg,
                               SkipReason skipReason, CompletionThresholds thresholds) {
        if (status != SessionStatus.IN_PROGRESS)
            throw new DomainRuleViolationException(
                    "La sesion ya se cerro como %s: no admite mas registros.".formatted(status));

        var exercise = findExercise(target.plannedExerciseId())
                .orElseGet(() -> {
                    var created = new WorkoutSessionExercise(this, target.plannedExerciseId());
                    exercises.add(created);
                    return created;
                });

        exercise.record(target, actualSets, actualRepsTotal, actualDurationSeconds,
                actualLoadKg, skipReason, thresholds);
    }

    /**
     * Cierra la sesion. assignedExercises es el numero de ejercicios INDICADOS,
     * no los registrados: si el usuario hizo tres de seis, el denominador siguen
     * siendo seis. Usar los registrados daria 100 % a media sesion.
     */
    public void finish(Short sessionRpe, Short satisfaction, Short activeMinutes,
                       int assignedExercises, CompletionThresholds thresholds) {
        if (status != SessionStatus.IN_PROGRESS)
            throw new DomainRuleViolationException("Esta sesion ya esta cerrada.");

        requireRpe(sessionRpe);
        requireSatisfaction(satisfaction);

        this.sessionRpe = sessionRpe;
        this.satisfaction = satisfaction;
        this.endedAt = OffsetDateTime.now();
        this.activeMinutes = activeMinutes != null ? activeMinutes : elapsedMinutes();
        this.completionPercentage = computeCompletion(assignedExercises, thresholds);

        // 17.2 clasifica en tres tramos, pero ck_session_status solo admite
        // cuatro estados y SKIPPED no esta entre ellos: por debajo del 30 % la
        // sesion se guarda como PARTIAL y es el entrenamiento del plan el que
        // se cierra como SKIPPED. El nivel completo viaja en completionLevel().
        this.status = thresholds.classify(this.completionPercentage.doubleValue()).persistable();

        // Sigue contando aunque sea un intento pobre: 17.4 dice que por cada
        // entrenamiento se toma la sesion marcada, y el porcentaje ya refleja
        // lo poco que se hizo. Excluirla la convertiria en un cero silencioso.
        this.countsTowardAdherence = true;
    }

    /** El usuario la dejo a medias sin declarar resultado. No cuenta como intento valido. */
    public void abandon() {
        if (status != SessionStatus.IN_PROGRESS)
            throw new DomainRuleViolationException("Esta sesion ya esta cerrada.");
        this.status = SessionStatus.ABANDONED;
        this.endedAt = OffsetDateTime.now();
        this.countsTowardAdherence = false;
    }

    /** Un intento posterior la desplaza: solo el ultimo finalizado cuenta. */
    public void supersede() {
        this.countsTowardAdherence = false;
    }

    // --------------------------------------------------------------- consultas

    public List<WorkoutSessionExercise> exercisesView() {
        return Collections.unmodifiableList(exercises);
    }

    public Optional<WorkoutSessionExercise> findExercise(Long plannedExerciseId) {
        return exercises.stream()
                .filter(exercise -> exercise.getPlannedExerciseId().equals(plannedExerciseId))
                .findFirst();
    }

    public int completedExerciseCount() {
        return (int) exercises.stream().filter(WorkoutSessionExercise::isCompleted).count();
    }

    /** Intento valido (17.3, metrica de frecuencia): finalizado y por encima del 30 %. */
    public boolean isValidAttempt(CompletionThresholds thresholds) {
        return status.isFinished()
                && completionPercentage.doubleValue() >= thresholds.sessionValidThresholdPct();
    }

    /** Nivel de 17.2 sin colapsar: es lo que se refleja en planned_workouts.status. */
    public SessionStatus completionLevel(CompletionThresholds thresholds) {
        if (status == SessionStatus.IN_PROGRESS || status == SessionStatus.ABANDONED) return status;
        return thresholds.classify(completionPercentage.doubleValue());
    }

    /** Causa dominante de los ejercicios saltados. Insumo del ajuste. */
    public Optional<SkipReason> dominantSkipReason() {
        return exercises.stream()
                .map(WorkoutSessionExercise::getSkipReason)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(reason -> reason,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey);
    }

    // ------------------------------------------------------------------ helpers

    private BigDecimal computeCompletion(int assignedExercises, CompletionThresholds thresholds) {
        if (assignedExercises <= 0) return BigDecimal.ZERO;

        var total = exercises.stream()
                .map(WorkoutSessionExercise::getCompletionPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var average = total.divide(BigDecimal.valueOf(assignedExercises), 2, RoundingMode.HALF_UP);
        return average.min(BigDecimal.valueOf(thresholds.completionCapPct())).max(BigDecimal.ZERO);
    }

    private Short elapsedMinutes() {
        if (endedAt == null) return null;
        long minutes = Duration.between(startedAt, endedAt).toMinutes();
        return (short) Math.max(0, Math.min(minutes, Short.MAX_VALUE));
    }

    private static void requireRpe(Short sessionRpe) {
        if (sessionRpe != null && (sessionRpe < 1 || sessionRpe > 10))
            throw new DomainRuleViolationException("El esfuerzo percibido va de 1 a 10.");
    }

    private static void requireSatisfaction(Short satisfaction) {
        if (satisfaction != null && (satisfaction < 1 || satisfaction > 5))
            throw new DomainRuleViolationException("La satisfaccion va de 1 a 5.");
    }
}