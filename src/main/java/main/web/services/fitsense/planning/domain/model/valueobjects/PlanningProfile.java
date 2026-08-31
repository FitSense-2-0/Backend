package main.web.services.fitsense.planning.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.List;

/** Vista del perfil traducida al lenguaje de planning (bloque "user" y "constraints" de 19.1). */
public record PlanningProfile(
        Long userId,
        int age,
        String biologicalSex,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal targetWeightKg,
        String fitnessLevel,
        String goalType,
        String goalText,
        String healthNotes,
        String trainingLocation,
        int daysPerWeek,
        List<Short> availableDays,
        int sessionMinutes,
        List<String> equipmentCodes,
        List<Long> blockedExerciseIds
) {
    /** BEGINNER -> 1, INTERMEDIATE -> 2, ADVANCED -> 3 (20.3). */
    public int maxDifficultyLevel() {
        return switch (String.valueOf(fitnessLevel)) {
            case "BEGINNER" -> 1;
            case "ADVANCED" -> 3;
            default -> 2;
        };
    }

    public boolean excludesGymOnlyEquipment() {
        return "HOME".equals(trainingLocation);
    }

    /** Validacion 4: ninguna sesion excede session_minutes + 15 %. */
    public int maxSessionMinutes() {
        return (int) Math.round(sessionMinutes * 1.15);
    }
}
