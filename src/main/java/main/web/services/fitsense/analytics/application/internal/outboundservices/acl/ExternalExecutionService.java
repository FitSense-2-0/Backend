package main.web.services.fitsense.planning.application.internal.outboundservices.acl;

import main.web.services.fitsense.execution.interfaces.acl.ExecutionContextFacade;
import main.web.services.fitsense.execution.interfaces.acl.WorkoutResultView;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Capa anticorrupcion hacia execution: lo que la persona hizo la semana
 * anterior, para que el generador ajuste con datos reales y no solo con lo que
 * se le pidio.
 * <p>
 * Nombre de bean explicito: analytics ya tiene un ExternalExecutionService y
 * dos beans con el mismo nombre por defecto impiden arrancar la aplicacion.
 */
@Service("planningExternalExecutionService")
public class ExternalExecutionService {

    private final ExecutionContextFacade executionContextFacade;

    public ExternalExecutionService(ExecutionContextFacade executionContextFacade) {
        this.executionContextFacade = executionContextFacade;
    }

    /** Clave: planned_workout_id. Un entrenamiento sin sesion que cuente no aparece. */
    public Map<Long, WorkoutResultView> fetchResultsByWorkout(Collection<Long> plannedWorkoutIds) {
        return executionContextFacade.fetchCountedResults(plannedWorkoutIds).stream()
                .collect(Collectors.toMap(WorkoutResultView::plannedWorkoutId, Function.identity(),
                        // El indice ux_session_counted garantiza uno por
                        // entrenamiento; si llegaran dos, gana el primero en vez
                        // de reventar la generacion del plan.
                        (first, second) -> first));
    }
}