package main.web.services.fitsense.planning.infrastructure.generation.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * La estructura exacta de 19.1. Es a la vez lo que se envia a la IA y lo que se
 * persiste en input_snapshot: si el plan se cuestiona, la evidencia es
 * literalmente el mismo objeto, no una reconstruccion.
 * <p>
 * Vive en infrastructure y no en el dominio porque su forma la fija el contrato
 * con el proveedor, no el modelo de negocio.
 */
public record PlanInputSnapshot(
        @JsonProperty("schema_version") String schemaVersion,
        User user,
        Constraints constraints,
        Adjustment adjustment,
        @JsonProperty("previous_week") PreviousWeek previousWeek,
        @JsonProperty("available_exercises") List<AvailableExercise> availableExercises,
        @JsonProperty("safety_notes") String safetyNotes
) {

    public record User(
            Integer age,
            @JsonProperty("biological_sex") String biologicalSex,
            @JsonProperty("height_cm") BigDecimal heightCm,
            @JsonProperty("weight_kg") BigDecimal weightKg,
            @JsonProperty("target_weight_kg") BigDecimal targetWeightKg,
            @JsonProperty("fitness_level") String fitnessLevel,
            @JsonProperty("goal_type") String goalType,
            @JsonProperty("goal_text") String goalText,
            @JsonProperty("health_notes") String healthNotes) {}

    public record Constraints(
            @JsonProperty("week_number") short weekNumber,
            @JsonProperty("week_start_date") LocalDate weekStartDate,
            @JsonProperty("week_end_date") LocalDate weekEndDate,
            @JsonProperty("days_per_week") int daysPerWeek,
            @JsonProperty("available_days") List<Short> availableDays,
            @JsonProperty("session_minutes") int sessionMinutes,
            @JsonProperty("training_location") String trainingLocation,
            @JsonProperty("max_difficulty_level") int maxDifficultyLevel) {}

    public record Adjustment(
            List<String> types,
            @JsonProperty("target_volume") Integer targetVolume,
            @JsonProperty("target_volume_min") Integer targetVolumeMin,
            @JsonProperty("target_volume_max") Integer targetVolumeMax,
            @JsonProperty("target_volume_change_pct") double targetVolumeChangePct,
            @JsonProperty("load_change_pct") double loadChangePct,
            @JsonProperty("max_difficulty_level") Integer maxDifficultyLevel,
            String reason,
            @JsonProperty("dominant_skip_reason") String dominantSkipReason,
            @JsonProperty("distribution_hint") String distributionHint) {}

    public record PreviousWeek(
            @JsonProperty("weighted_adherence_pct") BigDecimal weightedAdherencePct,
            @JsonProperty("average_session_rpe") BigDecimal averageSessionRpe,
            @JsonProperty("total_volume") Integer totalVolume,
            @JsonProperty("body_part_distribution") Map<String, Integer> bodyPartDistribution,
            List<Prescription> prescriptions) {}

    public record Prescription(
            @JsonProperty("exercise_id") Long exerciseId,
            String name,
            Short sets,
            Short reps,
            @JsonProperty("load_kg") BigDecimal loadKg,
            @JsonProperty("completion_pct") BigDecimal completionPct,
            String status) {}

    public record AvailableExercise(
            @JsonProperty("exercise_id") Long exerciseId,
            String name,
            @JsonProperty("body_part") String bodyPart,
            String equipment,
            int difficulty) {}

    public static PlanInputSnapshot of(PlanGenerationContext context) {
        var profile = context.profile();

        var user = new User(profile.age(), profile.biologicalSex(), profile.heightCm(),
                profile.weightKg(), profile.targetWeightKg(), profile.fitnessLevel(),
                profile.goalType(), profile.goalText(), profile.healthNotes());

        var constraints = new Constraints(context.weekNumber(), context.weekStartDate(),
                context.weekEndDate(), context.effectiveDaysPerWeek(), profile.availableDays(),
                context.effectiveSessionMinutes(), profile.trainingLocation(),
                context.effectiveMaxDifficulty());

        var adjustment = context.adjustment() == null ? null : new Adjustment(
                context.adjustment().types().stream().map(Enum::name).toList(),
                context.adjustment().targetVolume(),
                context.adjustment().targetVolumeMin(),
                context.adjustment().targetVolumeMax(),
                context.adjustment().targetVolumeChangePct(),
                context.adjustment().loadChangePct(),
                context.adjustment().maxDifficultyLevel(),
                context.adjustment().reason(),
                context.adjustment().dominantSkipReason(),
                context.adjustment().distributionHint());

        var previous = context.previousWeek();
        var previousWeek = !previous.exists() ? null : new PreviousWeek(
                previous.weightedAdherencePct(), previous.averageSessionRpe(),
                previous.totalVolume(), previous.bodyPartDistribution(),
                previous.prescriptions().stream()
                        .map(outcome -> new Prescription(outcome.exerciseId(), outcome.name(),
                                outcome.sets(), outcome.reps(), outcome.loadKg(),
                                outcome.completionPct(), outcome.status()))
                        .toList());

        // El orden importa mas de lo que parece. Con la lista ordenada por
        // bodyPartId, difficulty, id —como sale del repositorio— un modelo
        // pequeno barre desde el principio: en la prueba con 405 ejercicios
        // devolvio los ids 128, 129, 130... consecutivos, y produjo un
        // FULL_BODY compuesto solo de biceps y triceps.
        //
        // Se agrupa por grupo muscular y se baraja dentro de cada grupo. Asi
        // "los primeros de la lista" ya no son todos de la misma zona, y el
        // modelo tiene que leer el body_part para elegir.
        //
        // La semilla sale del usuario y la semana, no del reloj: el mismo
        // participante en la misma semana ve el catalogo en el mismo orden, y
        // un plan se puede reproducir al depurar.
        var porGrupo = new java.util.LinkedHashMap<String, java.util.List<AvailableExercise>>();
        context.availableExercises().forEach(candidate -> porGrupo
                .computeIfAbsent(candidate.bodyPartCode(), key -> new java.util.ArrayList<>())
                .add(new AvailableExercise(candidate.exerciseId(), candidate.name(),
                        candidate.bodyPartCode(), candidate.equipmentCode(), candidate.difficulty())));

        var random = new java.util.Random(
                context.userId() * 1_000_003L + context.weekStartDate().toEpochDay());
        var exercises = new java.util.ArrayList<AvailableExercise>();
        var grupos = new java.util.ArrayList<>(porGrupo.keySet());
        java.util.Collections.shuffle(grupos, random);
        grupos.forEach(grupo -> {
            var delGrupo = porGrupo.get(grupo);
            java.util.Collections.shuffle(delGrupo, random);
            exercises.addAll(delGrupo);
        });

        // El filtro ya retiro los ejercicios prohibidos. Este texto va ademas
        // para que el modelo modere la prescripcion de los que SI quedan: una
        // sentadilla sigue siendo elegible y puede pautarse mas o menos profunda.
        return new PlanInputSnapshot(PlanGenerationContext.SCHEMA_VERSION,
                user, constraints, adjustment, previousWeek, exercises,
                context.safety() == null ? null : context.safety().describe());
    }
}
