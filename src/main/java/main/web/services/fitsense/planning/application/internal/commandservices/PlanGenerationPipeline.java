package main.web.services.fitsense.planning.application.internal.commandservices;

import main.web.services.fitsense.planning.domain.exceptions.AiProviderUnavailableException;
import main.web.services.fitsense.planning.domain.exceptions.InvalidPlanDraftException;
import main.web.services.fitsense.planning.domain.exceptions.PlanGenerationFailedException;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanDraft;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;
import main.web.services.fitsense.planning.domain.services.PlanDraftValidator;
import main.web.services.fitsense.planning.domain.services.TrainingPlanGenerator;
import main.web.services.fitsense.planning.infrastructure.generation.ai.ReplicateTrainingPlanGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Fallos seguidos del proveedor antes de dejar de intentarlo. */
    private static final int UMBRAL_CORTOCIRCUITO = 3;

    /** Cuanto se espera antes de volver a probar con la IA. */
    private static final Duration DESCANSO = Duration.ofMinutes(10);

    private final ReplicateTrainingPlanGenerator aiGenerator;
    private final TrainingPlanGenerator ruleGenerator;
    private final PlanDraftValidator validator;

    private final AtomicInteger fallosSeguidos = new AtomicInteger();
    private volatile Instant reabrirDespuesDe = Instant.EPOCH;

    public PlanGenerationPipeline(ReplicateTrainingPlanGenerator aiGenerator,
                                  @Qualifier("ruleBasedTrainingPlanGenerator")
                                  TrainingPlanGenerator ruleGenerator,
                                  PlanDraftValidator validator) {
        this.aiGenerator = aiGenerator;
        this.ruleGenerator = ruleGenerator;
        this.validator = validator;
    }

    /** @param attempts numero de intentos consumidos, para generation_attempts. */
    public record Result(PlanDraft draft, short attempts) {}

    public Result run(PlanGenerationContext context, int durationToRepsDivisor) {
        short attempts = 0;
        List<String> problems = List.of();

        if (aiGenerator.isEnabled() && proveedorDisponible()) {
            for (int attempt = 1; attempt <= AI_ATTEMPTS; attempt++) {
                attempts++;
                try {
                    var draft = aiGenerator.generate(context, problems);
                    validator.validate(draft, context, durationToRepsDivisor);
                    fallosSeguidos.set(0);
                    return new Result(draft, attempts);

                } catch (AiProviderUnavailableException e) {
                    // El proveedor esta caido: no tiene sentido gastar el segundo
                    // intento, va a fallar igual.
                    registrarFalloDeProveedor();
                    problems = e.problems();
                    break;

                } catch (InvalidPlanDraftException e) {
                    // El modelo respondio pero incumplio validaciones. Eso NO
                    // cuenta para el cortocircuito: es su capacidad, no una caida.
                    fallosSeguidos.set(0);
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

    /**
     * Cortacircuitos. Sin el, con Replicate caido cada participante del cierre
     * semanal espera su timeout completo: con diez participantes y 90 segundos
     * son quince minutos, y todos acaban en el motor de reglas igualmente.
     * <p>
     * Es una politica del estudio, no una preocupacion generica de red, por eso
     * no se trae una libreria: el respaldo por reglas ya existe y lo unico que
     * hace falta es dejar de esperar.
     */
    private boolean proveedorDisponible() {
        if (Instant.now().isBefore(reabrirDespuesDe)) {
            log.debug("Cortocircuito abierto: se va directo al motor de reglas");
            return false;
        }
        return true;
    }

    private void registrarFalloDeProveedor() {
        int seguidos = fallosSeguidos.incrementAndGet();
        if (seguidos >= UMBRAL_CORTOCIRCUITO) {
            reabrirDespuesDe = Instant.now().plus(DESCANSO);
            fallosSeguidos.set(0);
            log.error("El proveedor de IA fallo {} veces seguidas. Se usa el motor de "
                    + "reglas durante {} minutos.", seguidos, DESCANSO.toMinutes());
        }
    }
}