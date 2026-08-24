package main.web.services.fitsense.profiling.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import main.web.services.fitsense.profiling.domain.model.valueobjects.BiologicalSex;
import main.web.services.fitsense.profiling.domain.model.valueobjects.FitnessLevel;
import main.web.services.fitsense.profiling.domain.model.valueobjects.GoalType;
import main.web.services.fitsense.profiling.domain.model.valueobjects.TrainingLocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateUserProfileResource(
        @NotNull @Past LocalDate birthDate,
        BiologicalSex biologicalSex,
        @NotNull @DecimalMin("120.0") @DecimalMax("250.0") BigDecimal heightCm,
        @NotNull @DecimalMin("30.0") @DecimalMax("300.0") BigDecimal currentWeightKg,
        @DecimalMin("30.0") @DecimalMax("300.0") BigDecimal targetWeightKg,
        @NotNull FitnessLevel fitnessLevel,
        @NotNull GoalType goalType,
        @NotBlank @Size(max = 500) String goalText,
        @NotNull TrainingLocation trainingLocation,
        @NotNull @Min(1) @Max(7) Short daysPerWeek,
        @NotEmpty List<@Min(1) @Max(7) Short> availableDays,
        @NotNull @Min(15) @Max(180) Short sessionMinutes,
        List<String> equipmentCodes,
        List<Long> blockedExerciseIds,
        @Size(max = 500) String healthNotes
) {}
