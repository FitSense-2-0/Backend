package main.web.services.fitsense.planning.application.internal.queryservices;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.entities.PlannedWorkout;
import main.web.services.fitsense.planning.domain.model.queries.*;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanQueryService;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.PlannedWorkoutRepository;
import main.web.services.fitsense.planning.infrastructure.persistence.jpa.repositories.WeeklyTrainingPlanRepository;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WeeklyTrainingPlanQueryServiceImpl implements WeeklyTrainingPlanQueryService {

    private final WeeklyTrainingPlanRepository planRepository;
    private final PlannedWorkoutRepository plannedWorkoutRepository;

    public WeeklyTrainingPlanQueryServiceImpl(WeeklyTrainingPlanRepository planRepository,
                                              PlannedWorkoutRepository plannedWorkoutRepository) {
        this.planRepository = planRepository;
        this.plannedWorkoutRepository = plannedWorkoutRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklyTrainingPlan> handle(GetActivePlanQuery query) {
        var plan = planRepository.findActiveOn(query.userId(), query.onDate());
        plan.ifPresent(WeeklyTrainingPlan::workoutsView); // fuerza la carga dentro de la transaccion
        return plan;
    }

    /**
     * El userId no es opcional en la firma: un plan solo se devuelve a su dueno.
     * Filtrar aqui y no en el controlador evita que un endpoint nuevo se olvide
     * de comprobarlo.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklyTrainingPlan> handle(GetPlanByIdQuery query) {
        return planRepository.findById(query.planId())
                .filter(plan -> plan.getUserId().equals(query.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklyTrainingPlan> handle(GetPlanByUserAndWeekQuery query) {
        return planRepository.findByUserIdAndWeekStartDateAndStatus(
                query.userId(), query.weekStartDate(), PlanStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyTrainingPlan> handle(GetPlanHistoryQuery query) {
        return planRepository.findByUserIdOrderByWeekStartDateDescPlanVersionDesc(query.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlannedWorkout> handle(GetPlannedWorkoutByIdQuery query) {
        return plannedWorkoutRepository.findById(query.plannedWorkoutId());
    }
}
