package main.web.services.fitsense.shared.infrastructure.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.stereotype.Component;

/**
 * Unico punto donde el backend serializa y deserializa las columnas JSONB
 * (params, input_snapshot, output_snapshot, risk_factors).
 * <p>
 * Usa un ObjectMapper propio, NO el de Spring MVC: el JSON que va a la base
 * usa snake_case y no debe cambiar si manana alguien ajusta la serializacion
 * de la API. Los dos formatos son independientes a proposito.
 */
@Component
public class JsonSupport {

    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar a JSON: " + e.getMessage(), e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo leer el JSON hacia %s: %s".formatted(type.getSimpleName(), e.getMessage()), e);
        }
    }
}
