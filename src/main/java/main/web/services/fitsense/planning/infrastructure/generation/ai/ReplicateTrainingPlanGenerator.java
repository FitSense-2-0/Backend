package main.web.services.fitsense.planning.infrastructure.generation.ai;

import main.web.services.fitsense.planning.domain.exceptions.AiProviderUnavailableException;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReplicateTrainingPlanGenerator implements TrainingPlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReplicateTrainingPlanGenerator.class);

    /** Espera inicial de la peticion. Replicate no admite mas de 60. */
    private static final int PREFER_WAIT_SECONDS = 60;

    /** Cada cuanto se vuelve a preguntar por una prediccion en curso. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);

    private final ReplicateProperties properties;
    private final PlanPromptBuilder promptBuilder;
    private final PlanDraftFromJsonAssembler assembler;
    private final JsonSupport jsonSupport;
    private final RestClient restClient;

    public ReplicateTrainingPlanGenerator(ReplicateProperties properties,
                                          PlanPromptBuilder promptBuilder,
                                          PlanDraftFromJsonAssembler assembler,
                                          JsonSupport jsonSupport) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.assembler = assembler;
        this.jsonSupport = jsonSupport;
        this.restClient = RestClient.create();
    }

    @Override
    public GenerationSource source() { return GenerationSource.AI; }

    @Override
    public String modelName() { return properties.model(); }

    public boolean isEnabled() { return properties.isUsable(); }

    @Override
    public PlanDraft generate(PlanGenerationContext context, List<String> previousProblems) {
        return toDraft(requestOutput(context, AiRetryFeedback.ofProblems(previousProblems)));
    }

    /**
     * Pide una propuesta y devuelve el JSON tal cual lo entrego el modelo. Va
     * separado de {@link #toDraft(String)} para que el pipeline conserve la
     * propuesta aunque luego se rechace: el reintento la necesita para
     * corregirla (AiRetryFeedback).
     */
    public String requestOutput(PlanGenerationContext context, AiRetryFeedback feedback) {
        var snapshot = PlanInputSnapshot.of(context);
        var snapshotJson = jsonSupport.write(snapshot);
        var prompt = promptBuilder.build(context, snapshotJson, feedback);
        return call(prompt, context.effectiveDaysPerWeek());
    }

    /** Convierte la salida del modelo en borrador. Lanza InvalidPlanDraftException si no se puede. */
    public PlanDraft toDraft(String output) {
        return assembler.toDraft(output, properties.model());
    }

    private String call(String prompt, int workoutCount) {
        var input = new LinkedHashMap<String, Object>();
        input.put("model", properties.model());
        input.put("prompt", prompt);
        input.put("reasoning_effort", properties.reasoningEffort());
        input.put("json_schema", PlanJsonSchema.format(workoutCount));

        String rawBody = post(input);

        // Respuesta inicial de Replicate: repite el prompt entero (unos 160 KB con
        // el catalogo) y casi siempre llega con status "starting" y output null,
        // asi que no muestra el plan. Queda en DEBUG; la salida real del modelo
        // se registra en INFO mas abajo, cuando la prediccion termina.
        log.debug("Replicate respondio (crudo): {}", rawBody);

        var response = jsonSupport.read(rawBody, ReplicateResponse.class);

        // Prefer: wait tiene un techo duro de 60 s del lado de Replicate. Con el
        // catalogo completo el modelo tarda mas, y la respuesta llegaba con
        // estado "starting": no era un fallo, era una prediccion aun en curso
        // que se contabilizaba como intento perdido.
        if (response != null && response.pending()) {
            response = awaitCompletion(response);
        }

        if (response == null || !response.succeeded()) {
            String detail = response == null
                    ? "respuesta vacia"
                    : response.status() + " " + response.error();
            log.warn("Replicate devolvio un estado no exitoso: {}", detail);

            // Excepcion de proveedor, no de plan invalido: el modelo no llego a
            // proponer nada. El cortacircuitos solo cuenta estas, y en el
            // analisis hay que poder separar "el modelo no supo" de "el
            // proveedor estaba caido".
            throw new AiProviderUnavailableException(detail);
        }

        String output = response.joinedOutput();

        // Evidencia de cada intento, tambien de los rechazados: la proporcion de
        // planes validos al primer intento es un resultado de la tesis y esta
        // linea es lo que permite revisar QUE propuso el modelo cada vez.
        log.info("Salida de la IA (prediccion {}, prompt {} {}, {} caracteres de prompt): {}",
                response.id(), PlanPromptBuilder.VERSION, PrescriptionPrinciples.VERSION,
                prompt.length(), output);
        return output;
    }

    /**
     * Consulta la prediccion hasta que termina. El limite lo marca
     * fitsense.ai.timeout-seconds, que ya NO esta atado a los 60 s de la
     * cabecera Prefer.
     */
    private ReplicateResponse awaitCompletion(ReplicateResponse pending) {
        var pollUrl = pending.pollUrl();
        if (pollUrl == null) {
            log.warn("La prediccion sigue en curso y no trae urls.get: no se puede consultar");
            return pending;
        }

        var limite = Instant.now().plusSeconds(properties.timeoutSeconds());
        var actual = pending;

        while (actual != null && actual.pending() && Instant.now().isBefore(limite)) {
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                // Restaurar la marca: alguien esta parando la aplicacion y no
                // queremos dejar el hilo en un estado inconsistente.
                Thread.currentThread().interrupt();
                return actual;
            }

            try {
                var raw = restClient.get()
                        .uri(pollUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                        .retrieve()
                        .body(String.class);
                actual = jsonSupport.read(raw, ReplicateResponse.class);
            } catch (RuntimeException e) {
                log.warn("Fallo al consultar la prediccion {}: {}", pending.id(), e.getMessage());
                return actual;
            }
        }

        if (actual != null && actual.pending())
            log.warn("La prediccion {} seguia en curso tras {} s", pending.id(),
                    properties.timeoutSeconds());

        return actual;
    }

    private String post(Map<String, Object> input) {
        try {
            return restClient.post()
                    .uri(properties.baseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                    .header("Prefer", "wait=" + PREFER_WAIT_SECONDS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("input", input))
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            log.warn("Replicate no respondio correctamente: {}", e.getMessage());
            throw new AiProviderUnavailableException(e.getMessage());
        }
    }
}
