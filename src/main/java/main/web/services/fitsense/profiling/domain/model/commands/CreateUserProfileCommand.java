package main.web.services.fitsense.profiling.domain.model.commands;

import main.web.services.fitsense.profiling.domain.model.valueobjects.BiologicalSex;
import main.web.services.fitsense.profiling.domain.model.valueobjects.FitnessLevel;
import main.web.services.fitsense.profiling.domain.model.valueobjects.GoalType;
import main.web.services.fitsense.profiling.domain.model.valueobjects.TrainingLocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateUserProfileCommand(
        Long userId,
        LocalDate birthDate,
        BiologicalSex biologicalSex,
        BigDecimal heightCm,
        BigDecimal currentWeightKg,
        BigDecimal targetWeightKg,
        FitnessLevel fitnessLevel,
        GoalType goalType,
        String goalText,
        TrainingLocation trainingLocation,
        Short daysPerWeek,
        List<Short> availableDays,
        Short sessionMinutes,
        List<String> equipmentCodes,
        List<Long> blockedExerciseIds,
        String healthNotes
) {}
