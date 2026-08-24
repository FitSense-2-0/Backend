package main.web.services.fitsense.profiling.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UserProfileResource(
        Long userId,
        LocalDate birthDate,
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
        List<String> equipmentCodes,
        List<Long> blockedExerciseIds,
        String healthNotes
) {}
