package main.web.services.fitsense.execution.application.internal.outboundservices.acl;

import main.web.services.fitsense.execution.domain.model.valueobjects.ExerciseTarget;
import main.web.services.fitsense.planning.interfaces.acl.PlannedWorkoutView;
import main.web.services.fitsense.planning.interfaces.acl.PlanningContextFacade;
import main.web.services.fitsense.shared.domain.model.valueobjects.SkipReason;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Capa anticorrupcion hacia planning. Execution nunca escribe en las tablas de
 * planificacion: pide lo indicado y comunica el desenlace.
 */
@Service
public class ExternalPlanningService {

    private final PlanningContextFacade planningContextFacade;

    public ExternalPlanningService(PlanningContextFacade planningContextFacade) {
        this.planningContextFacade = planningContextFacade;
    }

    public Optional<PlannedWorkoutView> fetchPlannedWorkout(Long plannedWorkoutId) {
        return planningContextFacade.fetchPlannedWorkout(plannedWorkoutId);
    }

    public List<ExerciseTarget> targetsOf(PlannedWorkoutView workout) {
        return workout.exercises().stream()
                .map(target -> new ExerciseTarget(
                        target.plannedExerciseId(),
                        target.exerciseId(),
                        "DURATION".equals(target.prescriptionType()),
                        target.plannedRepsTotal(),
                        target.plannedDurationSeconds()))
                .toList();
    }

    public void markInProgress(Long plannedWorkoutId) {
        planningContextFacade.markWorkoutInProgress(plannedWorkoutId);
    }

    public void recordOutcome(Long plannedWorkoutId, String outcome, SkipReason skipReason) {
        planningContextFacade.recordWorkoutOutcome(plannedWorkoutId, outcome, skipReason);
    }
}
