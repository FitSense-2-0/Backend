package main.web.services.fitsense.profiling.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Los dos juntos, a proposito: el texto libre enriquece el prompt de la IA y la
 * categoria da una variable de agrupacion manejable en el analisis (seccion 5).
 */
@Embeddable
public record TrainingGoal(
        @Enumerated(EnumType.STRING)
        @Column(name = "goal_type", nullable = false, length = 30) GoalType goalType,
        @Column(name = "goal_text", nullable = false, length = 500) String goalText
) {
    public TrainingGoal {
        if (goalType == null)
            throw new IllegalArgumentException("La categoria de meta es obligatoria.");
        if (goalText == null || goalText.isBlank())
            throw new IllegalArgumentException("Describe tu objetivo en tus propias palabras.");
        goalText = goalText.trim();
        if (goalText.length() > 500)
            throw new IllegalArgumentException("El objetivo no puede exceder 500 caracteres.");
    }
}
