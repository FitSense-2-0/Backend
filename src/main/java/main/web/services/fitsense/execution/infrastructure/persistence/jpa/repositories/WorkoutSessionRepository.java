package main.web.services.fitsense.execution.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.execution.domain.model.aggregates.WorkoutSession;
import main.web.services.fitsense.execution.domain.model.valueobjects.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    Optional<WorkoutSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    /** Tarea diaria: sesiones en curso desde hace demasiado (seccion 22). */
    java.util.List<WorkoutSession> findByStatusAndStartedAtBefore(
            SessionStatus status, OffsetDateTime startedBefore);

    List<WorkoutSession> findByPlannedWorkoutIdOrderByAttemptNumberAsc(Long plannedWorkoutId);

    /** Intentos previos que aun cuentan: hay que desplazarlos antes de que cuente el nuevo. */
    List<WorkoutSession> findByPlannedWorkoutIdAndCountsTowardAdherenceTrue(Long plannedWorkoutId);

    @Query("SELECT COALESCE(MAX(s.attemptNumber), 0) FROM WorkoutSession s "
            + "WHERE s.plannedWorkoutId = :plannedWorkoutId")
    short findLastAttemptNumber(@Param("plannedWorkoutId") Long plannedWorkoutId);

    /** Sesiones que cuentan en una semana. Es el numerador de la adherencia. */
    @Query("""
           SELECT s FROM WorkoutSession s
           WHERE s.userId = :userId
             AND s.countsTowardAdherence = true
             AND s.startedAt >= :from
             AND s.startedAt < :to
           ORDER BY s.startedAt ASC
           """)
    List<WorkoutSession> findCountedBetween(@Param("userId") Long userId,
                                            @Param("from") OffsetDateTime from,
                                            @Param("to") OffsetDateTime to);

    @Query("""
           SELECT MAX(s.startedAt) FROM WorkoutSession s
           WHERE s.userId = :userId AND s.countsTowardAdherence = true
           """)
    Optional<OffsetDateTime> findLastCountedSessionDate(@Param("userId") Long userId);
}
