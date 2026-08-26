package main.web.services.fitsense.planning.infrastructure.generation.ai;

import main.web.services.fitsense.planning.domain.exceptions.InvalidPlanDraftException;
import main.web.services.fitsense.planning.domain.model.valueobjects.GenerationSource;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanDraft;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;
import main.web.services.fitsense.planning.domain.services.TrainingPlanGenerator;
import main.web.services.fitsense.shared.infrastructure.json.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de Replicate. Llama a openai/gpt-5-structured pasando el modelo
 * concreto en el input, lo que permite forzar la forma de la salida con
 * json_schema.
 * <p>
 * La cabecera Prefer: wait hace la llamada sincrona: la prediccion vuelve ya
 * resuelta y no hace falta hacer polling ni guardar estado intermedio.
 * <p>
 * El token nunca aparece en el codigo ni en los logs: llega por variable de
 * entorno, como el resto de credenciales del proyecto.
 */
@Component
public class ReplicateTrainingPlanGenerator implements TrainingPlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReplicateTrainingPlanGenerator.class);

    private final ReplicateProperties properties;
    private final PlanPromptBuilder promptBuilder;
    private final PlanDraftFromJsonAssembler assembler;
    private final JsonSupport jsonSupport;
    private final RestClient restClient;

    public ReplicateTrainingPlanGenerator(ReplicateProperties properties,
                                          PlanPromptBuilder promptBuilder,
                                          PlanDraftFromJsonAssembler assembler,
                                          JsonSupport jsonSupport,
                                          RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.assembler = assembler;
        this.jsonSupport = jsonSupport;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public GenerationSource source() {
        return GenerationSource.AI;
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    public boolean isEnabled() {
        return properties.isUsable();
    }

    @Override
    public PlanDraft generate(PlanGenerationContext context, List<String> previousProblems) {
        var snapshot = PlanInputSnapshot.of(context);
        var snapshotJson = jsonSupport.write(snapshot);
        var prompt = promptBuilder.build(context, snapshotJson, previousProblems);

        String output = call(prompt);
        return assembler.toDraft(output, properties.model());
    }

    private String call(String prompt) {
        var input = new LinkedHashMap<String, Object>();
        input.put("model", properties.model());
        input.put("prompt", prompt);
        input.put("reasoning_effort", properties.reasoningEffort());
        input.put("json_schema", PlanJsonSchema.format());

        ReplicateResponse response;
        try {
            response = restClient.post()
                    .uri(properties.baseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                    // Prefer: wait bloquea hasta que la prediccion termina.
                    .header("Prefer", "wait=" + Math.min(properties.timeoutSeconds(), 60))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("input", input))
                    .retrieve()
                    .body(ReplicateResponse.class);
        } catch (RuntimeException e) {
            // Un fallo de red o un 4xx no es un plan invalido: es el proveedor
            // caido. Se trata igual a efectos de reintento, pero se registra
            // distinto para poder separarlos en el analisis.
            log.warn("Replicate no respondio correctamente: {}", e.getMessage());
            throw new InvalidPlanDraftException(List.of(
                    "El proveedor de IA no respondio: " + e.getMessage()));
        }

        if (response == null || !response.succeeded()) {
            String detail = response == null ? "respuesta vacia" : response.status() + " " + response.error();
            log.warn("Replicate devolvio un estado no exitoso: {}", detail);
            throw new InvalidPlanDraftException(List.of("El proveedor de IA fallo: " + detail));
        }

        return response.joinedOutput();
    }

    Duration timeout() {
        return Duration.ofSeconds(properties.timeoutSeconds());
    }
}
