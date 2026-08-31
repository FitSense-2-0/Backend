package main.web.services.fitsense.shared.infrastructure.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
            // Sin JavaTimeModule, un LocalDate revienta la serializacion. Al
            // construir el mapper a mano no hay autoconfiguracion que lo
            // registre, asi que hay que hacerlo aqui explicitamente.
            .registerModule(new JavaTimeModule())
            // Las fechas van como cadena ISO ("2026-08-31") y no como array de
            // enteros: input_snapshot es evidencia que alguien va a leer.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Replicate devuelve output como cadena o como arreglo de fragmentos
            // segun el modelo. Aceptar ambas formas evita un fallo de parseo que
            // se contaria como fallo del modelo.
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

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