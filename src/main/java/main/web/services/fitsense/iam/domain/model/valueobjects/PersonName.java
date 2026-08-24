package main.web.services.fitsense.iam.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record PersonName(
        @Column(name = "first_name", nullable = false, length = 100) String firstName,
        @Column(name = "last_name", length = 150) String lastName
) {
    public PersonName {
        if (firstName == null || firstName.isBlank())
            throw new IllegalArgumentException("El nombre es obligatorio.");
        firstName = firstName.trim();
        lastName = (lastName == null || lastName.isBlank()) ? null : lastName.trim();
    }

    public String fullName() {
        return lastName == null ? firstName : firstName + " " + lastName;
    }
}
