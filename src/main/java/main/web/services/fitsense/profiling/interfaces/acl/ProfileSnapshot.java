package main.web.services.fitsense.profiling.interfaces.acl;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista de solo lectura del perfil para los demas contextos. Es la base del
 * bloque {@code user} y {@code constraints} de input_snapshot (seccion 19.1).
 */
public record ProfileSnapshot(
        Long userId,
        int age,
        String biologicalSex,
        BigDecimal heightCm,
        BigDecimal currentWeightKg,
        BigDecimal targetWeightKg,
        String fitnessLevel,
        String goalType,
        String goalText,
        String trainingLocation,
        short daysPerWeek,
        List<Short> availableDays,
        short sessionMinutes,
        int maxSessionMinutesAllowed,
        int maxDifficultyLevel,
        List<String> equipmentCodes,
        List<Long> blockedExerciseIds,
        String healthNotes
) {
    public boolean excludesGymOnlyEquipment() {
        return "HOME".equals(trainingLocation);
    }
}
