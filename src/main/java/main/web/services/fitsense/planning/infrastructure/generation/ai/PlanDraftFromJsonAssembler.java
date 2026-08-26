package main.web.services.fitsense.planning.infrastructure.generation.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import main.web.services.fitsense.planning.domain.exceptions.InvalidPlanDraftException;
import main.web.services.fitsense.planning.domain.model.valueobjects.*;
import main.web.services.fitsense.shared.infrastructure.json.JsonSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Convierte la salida de 19.2 en un PlanDraft.
 * <p>
 * Lo que no encaja se reporta como problema de validacion y no como excepcion
 * tecnica: un enum desconocido o una fecha mal formada son fallos del modelo, y
 * como tales deben viajar en la lista que se le devuelve en el segundo intento.
 */
@Component
public class PlanDraftFromJsonAssembler {

    private final JsonSupport jsonSupport;

    public PlanDraftFromJsonAssembler(JsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    public PlanDraft toDraft(String json, String modelName) {
        AiPlan parsed;
        try {
            parsed = jsonSupport.read(stripCodeFences(json), AiPlan.class);
        } catch (RuntimeException e) {
            throw new InvalidPlanDraftException(List.of(
                    "La respuesta no es un JSON valido con la forma esperada: " + e.getMessage()));
        }

        if (parsed == null || parsed.workouts() == null || parsed.workouts().isEmpty())
            throw new InvalidPlanDraftException(List.of("La respuesta no contiene entrenamientos."));

        var problems = new java.util.ArrayList<String>();
        var workouts = new java.util.ArrayList<PlanDraft.DraftWorkout>();

        for (var workout : parsed.workouts()) {
            LocalDate date = parseDate(workout.scheduledDate(), problems);
            WorkoutFocus focus = parseEnum(WorkoutFocus.class, workout.focusCode(), "focus_code", problems);
            if (date == null || focus == null) continue;

            var exercises = new java.util.ArrayList<PlanDraft.DraftExercise>();
            if (workout.exercises() != null) {
                for (var exercise : workout.exercises()) {
                    var type = parseEnum(PrescriptionType.class, exercise.prescriptionType(),
                            "prescription_type", problems);
                    if (type == null || exercise.exerciseId() == null) continue;

                    exercises.add(new PlanDraft.DraftExercise(
                            exercise.exerciseId(), type,
                            toShort(exercise.plannedSets()),
                            toShort(exercise.plannedReps()),
                            exercise.plannedDurationSeconds(),
                            exercise.targetLoadKg(),
                            toShort(exercise.restSeconds()),
                            exercise.notes()));
                }
            }

            workouts.add(new PlanDraft.DraftWorkout(date, focus,
                    workout.workoutName() == null ? focus.name() : workout.workoutName(),
                    workout.expectedDurationMinutes() == null ? 0 : workout.expectedDurationMinutes(),
                    List.copyOf(exercises)));
        }

        if (!problems.isEmpty()) throw new InvalidPlanDraftException(problems);

        return new PlanDraft(GenerationSource.AI, modelName, parsed.planName(),
                parsed.totalVolume(), parsed.rationale(), List.copyOf(workouts));
    }

    /**
     * Con json_schema no deberian aparecer, pero un modelo pequeno a veces
     * envuelve la respuesta igual. Cuesta tres lineas y evita contar como fallo
     * del modelo algo que no lo es.
     */
    private String stripCodeFences(String raw) {
        if (raw == null) return "";
        var trimmed = raw.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstBreak < 0 || lastFence <= firstBreak) return trimmed;
        return trimmed.substring(firstBreak + 1, lastFence).trim();
    }

    private LocalDate parseDate(String raw, List<String> problems) {
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            problems.add("scheduled_date invalido: '%s'. Usa AAAA-MM-DD.".formatted(raw));
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field,
                                            List<String> problems) {
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase());
        } catch (RuntimeException e) {
            problems.add("%s invalido: '%s'.".formatted(field, raw));
            return null;
        }
    }

    private Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    // Estructura literal de 19.2. Se mapea aparte del dominio para que un cambio
    // del contrato con el proveedor no toque los value objects.
    private record AiPlan(
            @JsonProperty("schema_version") String schemaVersion,
            @JsonProperty("plan_name") String planName,
            @JsonProperty("total_volume") Integer totalVolume,
            String rationale,
            List<AiWorkout> workouts) {}

    private record AiWorkout(
            @JsonProperty("scheduled_date") String scheduledDate,
            @JsonProperty("focus_code") String focusCode,
            @JsonProperty("workout_name") String workoutName,
            @JsonProperty("expected_duration_minutes") Integer expectedDurationMinutes,
            List<AiExercise> exercises) {}

    private record AiExercise(
            @JsonProperty("exercise_id") Long exerciseId,
            @JsonProperty("exercise_order") Integer exerciseOrder,
            @JsonProperty("prescription_type") String prescriptionType,
            @JsonProperty("planned_sets") Integer plannedSets,
            @JsonProperty("planned_reps") Integer plannedReps,
            @JsonProperty("planned_duration_seconds") Integer plannedDurationSeconds,
            @JsonProperty("target_load_kg") BigDecimal targetLoadKg,
            @JsonProperty("rest_seconds") Integer restSeconds,
            String notes) {}
}
