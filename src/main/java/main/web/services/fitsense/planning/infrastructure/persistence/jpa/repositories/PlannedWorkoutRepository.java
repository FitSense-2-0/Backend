package main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Acceso directo a la entidad solo para lectura puntual desde execution, que
 * necesita un entrenamiento por id sin cargar la semana entera. Toda escritura
 * del contenido del plan pasa por el agregado.
 */
@Repository
public interface PlannedWorkoutRepository extends JpaRepository<PlannedWorkout, Long> {

    List<PlannedWorkout> findByPlan_IdOrderByDisplayOrderAsc(Long planId);

    /** Tarea diaria 00:30: vencidos y todavia abiertos. */
    @Query("""
           SELECT w FROM PlannedWorkout w
           WHERE w.expiresAt < :now
             AND w.status IN (
                 main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus.SCHEDULED,
                 main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus.IN_PROGRESS)
           """)
    List<PlannedWorkout> findOverdue(@Param("now") OffsetDateTime now);

    /** Ejercicios prescritos en los ultimos 7 dias, para no repetirlos (20.3). */
    @Query("""
           SELECT e.exerciseId FROM PlannedWorkout w JOIN w.exercises e
           WHERE w.plan.userId = :userId
             AND w.scheduledDate >= :from
             AND w.scheduledDate < :to
             AND w.status <> main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutStatus.REPLACED
           """)
    List<Long> findExerciseIdsUsedBetween(@Param("userId") Long userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    List<PlannedWorkout> findByPlan_UserIdAndScheduledDateBetweenAndStatusNot(
            Long userId, LocalDate from, LocalDate to, WorkoutStatus excluded);
}
