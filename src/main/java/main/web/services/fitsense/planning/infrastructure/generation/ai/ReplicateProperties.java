package main.web.services.fitsense.planning.infrastructure.generation.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del proveedor de IA.
 * <p>
 * El nombre del modelo es una propiedad y no una constante: cambiar de
 * gpt-5-nano a gpt-5-mini debe ser tocar la configuracion, no recompilar, para
 * que la eleccion del modelo pueda tratarse como una variable del estudio.
 * <p>
 * enabled arranca en false a proposito: el ciclo completo se prueba con el motor
 * de reglas sin gastar llamadas, y la IA se enciende cuando se quiere medir.
 */
@ConfigurationProperties(prefix = "fitsense.ai")
public record ReplicateProperties(
        boolean enabled,
        String apiToken,
        String baseUrl,
        String model,
        String reasoningEffort,
        int timeoutSeconds
) {
    public ReplicateProperties {
        if (baseUrl == null || baseUrl.isBlank())
            baseUrl = "https://api.replicate.com/v1/models/openai/gpt-5-structured/predictions";
        if (model == null || model.isBlank()) model = "gpt-5-nano";
        if (reasoningEffort == null || reasoningEffort.isBlank()) reasoningEffort = "medium";
        if (timeoutSeconds <= 0) timeoutSeconds = 90;
    }

    /**
     * Solo se considera utilizable si ademas hay token. Sin esta comprobacion,
     * encender la IA sin exportar la variable produciria un 401 por cada
     * participante durante el cierre del lunes.
     */
    public boolean isUsable() {
        return enabled && apiToken != null && !apiToken.isBlank();
    }
}
