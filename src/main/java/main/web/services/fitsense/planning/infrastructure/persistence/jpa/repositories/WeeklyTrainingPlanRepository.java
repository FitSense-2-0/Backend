package main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyTrainingPlanRepository extends JpaRepository<WeeklyTrainingPlan, Long> {

    Optional<WeeklyTrainingPlan> findByUserIdAndWeekStartDateAndStatus(
            Long userId, LocalDate weekStartDate, PlanStatus status);

    /** El plan vigente que cubre una fecha. ux_plan_active garantiza que es uno solo. */
    @Query("""
           SELECT p FROM WeeklyTrainingPlan p
           WHERE p.userId = :userId
             AND p.status = main.web.services.fitsense.planning.domain.model.valueobjects.PlanStatus.ACTIVE
             AND p.weekStartDate <= :onDate
             AND p.weekEndDate >= :onDate
           """)
    Optional<WeeklyTrainingPlan> findActiveOn(@Param("userId") Long userId,
                                              @Param("onDate") LocalDate onDate);

    /**
     * TODAS las versiones de una semana, reemplazadas incluidas.
     * <p>
     * Lo exige la construccion del denominador de 17.4: los entrenamientos ya
     * ejecutados de una version anterior siguen contando aunque esa version haya
     * sido reemplazada. Mirar solo el plan ACTIVE perderia sesiones reales.
     */
    List<WeeklyTrainingPlan> findByUserIdAndWeekStartDateOrderByPlanVersionAsc(
            Long userId, LocalDate weekStartDate);

    /**
     * Semana 1 del participante: referencia del tope acumulado de 18.4.
     * <p>
     * Se toma la version 1 y no la ultima a proposito: el limite del 40 % es
     * contra el volumen ORIGINAL, no contra el ya ajustado. Si fuera contra el
     * ajustado, tres reducciones del 20 % seguidas dejarian al participante en
     * el 51 % sin que ninguna pasara el tope por si sola.
     */
    Optional<WeeklyTrainingPlan> findFirstByUserIdAndWeekNumberOrderByPlanVersionAsc(
            Long userId, Short weekNumber);

    List<WeeklyTrainingPlan> findByUserIdOrderByWeekStartDateDescPlanVersionDesc(Long userId);

    /** Ultima semana generada, sin importar el estado: define el week_number siguiente. */
    Optional<WeeklyTrainingPlan> findFirstByUserIdOrderByWeekNumberDescPlanVersionDesc(Long userId);
}