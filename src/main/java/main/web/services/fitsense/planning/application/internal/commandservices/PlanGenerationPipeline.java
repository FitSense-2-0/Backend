package main.web.services.fitsense.planning.application.internal.commandservices;

import main.web.services.fitsense.planning.domain.exceptions.InvalidPlanDraftException;
import main.web.services.fitsense.planning.domain.exceptions.PlanGenerationFailedException;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanDraft;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;
import main.web.services.fitsense.planning.domain.services.PlanDraftValidator;
import main.web.services.fitsense.planning.domain.services.TrainingPlanGenerator;
import main.web.services.fitsense.planning.infrastructure.generation.ai.ReplicateTrainingPlanGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * La politica de fallo de 19.4, literal:
 * <pre>
 *   Intento 1 falla -> se reenvia con la lista de validaciones incumplidas.
 *   Intento 2 falla -> generador de reglas.
 *   Regla falla     -> no se activa la semana.
 * </pre>
 * El respaldo por reglas no es opcional: sin el, una caida del proveedor deja
 * participantes sin plan y produce perdida de datos irrecuperable.
 * <p>
 * Devuelve tambien cuantos intentos hicieron falta, porque la proporcion de
 * planes validos al primer intento es un resultado reportable de la tesis.
 */
@Component
public class PlanGenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(PlanGenerationPipeline.class);
    private static final int AI_ATTEMPTS = 2;

    private final ReplicateTrainingPlanGenerator aiGenerator;
    private final TrainingPlanGenerator ruleGenerator;
    private final PlanDraftValidator validator;

    public PlanGenerationPipeline(ReplicateTrainingPlanGenerator aiGenerator,
                                  @org.springframework.beans.factory.annotation.Qualifier(
                                          "ruleBasedTrainingPlanGenerator")
                                  TrainingPlanGenerator ruleGenerator,
                                  PlanDraftValidator validator) {
        this.aiGenerator = aiGenerator;
        this.ruleGenerator = ruleGenerator;
        this.validator = validator;
    }

    /** @param attempts número de intentos consumidos, para generation_attempts. */
    public record Result(PlanDraft draft, short attempts) {}

    public Result run(PlanGenerationContext context, int durationToRepsDivisor) {
        short attempts = 0;
        List<String> problems = List.of();

        if (aiGenerator.isEnabled()) {
            for (int attempt = 1; attempt <= AI_ATTEMPTS; attempt++) {
                attempts++;
                try {
                    var draft = aiGenerator.generate(context, problems);
                    validator.validate(draft, context, durationToRepsDivisor);
                    return new Result(draft, attempts);
                } catch (InvalidPlanDraftException e) {
                    problems = e.problems();
                    log.warn("Intento {} de IA rechazado para el usuario {}: {}",
                            attempt, context.userId(), problems);
                }
            }
        }

        // Respaldo determinista. Se cuenta como intento propio para que
        // generation_attempts refleje el coste real de producir la semana.
        attempts++;
        try {
            var draft = ruleGenerator.generate(context, problems);
            validator.validate(draft, context, durationToRepsDivisor);
            return new Result(draft, attempts);
        } catch (InvalidPlanDraftException e) {
            // El motor de reglas fallando indica que el conjunto elegible o el
            // perfil hacen imposible cualquier plan valido. Es un dato, no un
            // error tecnico: la semana queda sin plan y su adherencia sera NULL.
            log.error("El motor de reglas tampoco produjo un plan valido para el usuario {}: {}",
                    context.userId(), e.problems());
            throw new PlanGenerationFailedException(context.userId(),
                    "Ultimos problemas: " + String.join(" | ", e.problems()));
        }
    }
}
