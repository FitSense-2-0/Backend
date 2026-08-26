package main.web.services.fitsense.planning.infrastructure.generation.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta de Replicate. Con la cabecera Prefer: wait la prediccion llega ya
 * resuelta y no hace falta hacer polling.
 * <p>
 * output puede llegar como una cadena o como un arreglo de fragmentos segun el
 * modelo, asi que se modela como lista y se une.
 */
record ReplicateResponse(
        String id,
        String status,
        List<String> output,
        String error,
        @JsonProperty("logs") String logs
) {
    boolean succeeded() {
        return "succeeded".equals(status);
    }

    String joinedOutput() {
        return output == null ? "" : String.join("", output);
    }
}
