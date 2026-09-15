package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.model.aggregates.WeeklyTrainingPlan;
import main.web.services.fitsense.planning.domain.model.commands.*;
import main.web.services.fitsense.planning.domain.model.valueobjects.GeneratedPlan;
import main.web.services.fitsense.planning.domain.model.valueobjects.PreparedPlanGeneration;

import java.util.Optional;

/**
 * La generacion de un plan esta partida en tres fases a proposito. Ver el
 * javadoc de generate(): la del medio hace llamadas HTTP largas y no puede
 * ejecutarse dentro de una transaccion.
 * <p>
 * No existe un handle(GenerateWeeklyPlanCommand) de una sola pieza: mientras
 * existiera, cualquier llamador podria reintroducir el fallo sin darse cuenta.
 */
public interface WeeklyTrainingPlanCommandService {

    /** Fase 1: lee todo lo necesario para generar. Transaccional y corta. */
    PreparedPlanGeneration prepare(GenerateWeeklyPlanCommand command);

    /** Fase 2: llama al generador. SIN transaccion: tarda ~90 s de HTTP. */
    GeneratedPlan generate(PreparedPlanGeneration prepared);

    /** Fase 3: materializa y guarda. Transaccional y corta. */
    Optional<WeeklyTrainingPlan> persist(PreparedPlanGeneration prepared, GeneratedPlan generated);

    void handle(StartPlannedWorkoutCommand command);
    void handle(RecordWorkoutOutcomeCommand command);
    void handle(SkipPlannedWorkoutCommand command);
    void handle(ClosePlanWeekCommand command);
    int handle(ExpireOverdueWorkoutsCommand command);
}