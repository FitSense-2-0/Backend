package main.web.services.fitsense.planning.domain.exceptions;

import java.util.List;

/**
 * El proveedor de IA no respondio o fallo por su cuenta, a diferencia de haber
 * devuelto un plan que no cumple las validaciones.
 * <p>
 * La distincion importa para dos cosas: el cortacircuitos solo cuenta estos
 * fallos, y en el analisis hay que poder separar "el modelo no supo" de "el
 * proveedor estaba caido". Si se mezclan, la proporcion RULE_ENGINE frente a AI
 * mide la fiabilidad de Replicate en vez de la capacidad del modelo.
 */
public class AiProviderUnavailableException extends InvalidPlanDraftException {
    public AiProviderUnavailableException(String detalle) {
        super(List.of("El proveedor de IA no esta disponible: " + detalle));
    }
}