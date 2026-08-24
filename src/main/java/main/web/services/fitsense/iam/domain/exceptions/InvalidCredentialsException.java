package main.web.services.fitsense.iam.domain.exceptions;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Correo o contrasena incorrectos.");
    }
}
