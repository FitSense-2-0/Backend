package main.web.services.fitsense.shared.domain.exceptions;

/** El recurso no existe o no pertenece al usuario del JWT. Se traduce a HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super("%s no encontrado: %s".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
