package main.web.services.fitsense.iam.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

/** Correo normalizado en minusculas. La base tiene un CHECK que lo exige. */
@Embeddable
public record EmailAddress(@Column(name = "email", nullable = false, length = 254) String address) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    public EmailAddress {
        if (address == null || address.isBlank())
            throw new IllegalArgumentException("El correo es obligatorio.");
        address = address.trim().toLowerCase();
        if (address.length() > 254)
            throw new IllegalArgumentException("El correo no puede exceder 254 caracteres.");
        if (!FORMAT.matcher(address).matches())
            throw new IllegalArgumentException("El formato del correo no es valido.");
    }
}
