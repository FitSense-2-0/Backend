package main.web.services.fitsense.planning.infrastructure.generation.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;
import main.web.services.fitsense.planning.domain.services.PlanDraftValidator;

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
        /**
         * Version del texto de principios con el que se genero (P-1.0...).
         * Queda en input_snapshot: si el texto cambia a mitad del estudio, cada
         * plan sigue ligado al criterio que de verdad recibio la IA.
         */
        @JsonProperty("principles_version") String principlesVersion,
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
            @JsonProperty("health_notes") String healthNotes,
            /**
             * Herramientas que el participante declaro tener. Antes no viajaba:
             * la IA solo podia adivinarlas mirando que ejercicios le llegaban, y
             * no es lo mismo el equipamiento de un ejercicio que el del usuario.
             * El principio 6 decide repeticiones a partir de este dato.
             */
            @JsonProperty("equipment_codes") List<String> equipmentCodes) {}

    public record Constraints(
            @JsonProperty("week_number") short weekNumber,
            @JsonProperty("week_start_date") LocalDate weekStartDate,
            @JsonProperty("week_end_date") LocalDate weekEndDate,
            @JsonProperty("days_per_week") int daysPerWeek,
            @JsonProperty("available_days") List<Short> availableDays,
            @JsonProperty("session_minutes") int sessionMinutes,
            @JsonProperty("training_location") String trainingLocation,
            @JsonProperty("max_difficulty_level") int maxDifficultyLevel,
            @JsonProperty("rep_limits") RepLimits repLimits,
            /** V20: minutos minimos por sesion. null cuando hay orden de volumen. */
            @JsonProperty("min_session_minutes") Integer minSessionMinutes,
            /** V21: descanso maximo entre series. */
            @JsonProperty("max_rest_seconds") Integer maxRestSeconds,
            /**
             * Division semanal sugerida (WeeklySplitPlanner): fecha y enfoque de
             * cada sesion. Cumple la recuperacion de 48 h y la frecuencia; la IA
             * puede proponer otra si tambien cumple V13, V18 y V22.
             */
            @JsonProperty("suggested_split") List<SuggestedSession> suggestedSplit,
            /**
             * Solo con REDUCE_VOLUME: tope de series y repeticiones de cada
             * ejercicio que ya estaba la semana anterior (V19). null si no aplica.
             */
            @JsonProperty("reduce_volume_caps") List<ReduceVolumeCap> reduceVolumeCaps,
            /** Minimo de repeticiones por nivel: 8 para BEGINNER, null para el resto. */
            @JsonProperty("min_reps_for_level") Integer minRepsForLevel) {}

    public record ReduceVolumeCap(
            @JsonProperty("exercise_id") long exerciseId,
            @JsonProperty("max_sets") int maxSets,
            @JsonProperty("max_reps") int maxReps,
            /** COMPLETED: puede progresar un poco. HOLD: no puede subir. */
            String reason) {}

    public record SuggestedSession(
            @JsonProperty("scheduled_date") LocalDate scheduledDate,
            @JsonProperty("focus_code") String focusCode) {}

    /**
     * Limite amplio que verifica V16 (MVP-1.5). Reemplaza al rango por objetivo
     * (rep_range): NO es un objetivo ni una sugerencia, solo el borde de lo
     * absurdo. Dentro de el las repeticiones las decide la IA con los
     * principios.
     */
    public record RepLimits(
            @JsonProperty("min_reps") Integer minReps,
            @JsonProperty("max_reps") Integer maxReps,
            /** Minimo provisional por body_part (gemelos 12, abdomen 10). */
            @JsonProperty("min_reps_by_body_part") Map<String, Integer> minRepsByBodyPart) {}

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

    /**
     * GEN-IN-1.2: desempeno real por dia y por ejercicio. Antes era una lista
     * plana con lo PRESCRITO, completion_pct siempre null y el estado de la
     * sesion en lugar del ejercicio: la IA veia "COMPLETED" en un ejercicio
     * hecho a la mitad.
     * <p>
     * Anidado por dia a proposito: el RPE es de la sesion, y un modelo pequeno
     * relaciona mejor el esfuerzo con los ejercicios si van juntos.
     */
    public record PreviousWeek(
            @JsonProperty("weighted_adherence_pct") BigDecimal weightedAdherencePct,
            @JsonProperty("average_session_rpe") BigDecimal averageSessionRpe,
            @JsonProperty("total_volume") Integer totalVolume,
            @JsonProperty("body_part_distribution") Map<String, Integer> bodyPartDistribution,
            List<PreviousWorkout> workouts) {}

    public record PreviousWorkout(
            @JsonProperty("scheduled_date") LocalDate scheduledDate,
            @JsonProperty("focus_code") String focusCode,
            @JsonProperty("workout_status") String workoutStatus,
            @JsonProperty("skip_reason") String skipReason,
            /** false = no se registro; NO significa que se hizo al 0 %. */
            boolean recorded,
            @JsonProperty("session_rpe") Short sessionRpe,
            @JsonProperty("completion_pct") BigDecimal completionPct,
            List<PreviousExercise> exercises) {}

    public record PreviousExercise(
            @JsonProperty("exercise_id") Long exerciseId,
            String name,
            @JsonProperty("prescription_type") String prescriptionType,
            @JsonProperty("planned_sets") Short plannedSets,
            @JsonProperty("planned_reps") Short plannedReps,
            @JsonProperty("planned_duration_seconds") Integer plannedDurationSeconds,
            @JsonProperty("actual_sets") Short actualSets,
            @JsonProperty("actual_reps_total") Integer actualRepsTotal,
            @JsonProperty("actual_duration_seconds") Integer actualDurationSeconds,
            @JsonProperty("actual_load_kg") BigDecimal actualLoadKg,
            @JsonProperty("completion_pct") BigDecimal completionPct,
            @JsonProperty("exercise_status") String exerciseStatus,
            @JsonProperty("skip_reason") String skipReason) {}

    public record AvailableExercise(
            @JsonProperty("exercise_id") Long exerciseId,
            String name,
            @JsonProperty("body_part") String bodyPart,
            String equipment,
            int difficulty,
            /**
             * SETS_REPS o DURATION. Antes no viajaba, y por eso llegaron un
             * estiramiento a 3x7 y un planche a 2x60 s: la IA no sabia si el
             * ejercicio era de repeticiones o de sosten. V7 exige respetarlo.
             */
            @JsonProperty("prescription_type") String prescriptionType,
            /** biceps, triceps, pectorals...: separa lo que body_part mezcla (V22). */
            @JsonProperty("target_muscle") String targetMuscle) {}

    public static PlanInputSnapshot of(PlanGenerationContext context) {
        var profile = context.profile();

        var user = new User(profile.age(), profile.biologicalSex(), profile.heightCm(),
                profile.weightKg(), profile.targetWeightKg(), profile.fitnessLevel(),
                profile.goalType(), profile.goalText(), profile.healthNotes(),
                profile.equipmentCodes() == null ? List.of() : List.copyOf(profile.equipmentCodes()));

        var limites = context.prescription() == null ? null : context.prescription().repLimits();

        var constraints = new Constraints(context.weekNumber(), context.weekStartDate(),
                context.weekEndDate(), context.effectiveDaysPerWeek(), profile.availableDays(),
                context.effectiveSessionMinutes(), profile.trainingLocation(),
                context.effectiveMaxDifficulty(),
                limites == null || !limites.isComplete() ? null
                        : new RepLimits(limites.minReps(), limites.maxReps(),
                        limites.minRepsByBodyPart() == null ? Map.of() : limites.minRepsByBodyPart()),
                minSessionMinutesOrNull(context),
                context.prescription() == null ? null : context.prescription().maxRestSecondsOrDefault(),
                context.suggestedSplit().stream()
                        .map(session -> new SuggestedSession(session.date(), session.focus().name()))
                        .toList(),
                reduceVolumeCapsOrNull(context),
                "BEGINNER".equals(profile.fitnessLevel())
                        ? main.web.services.fitsense.configuration.domain.model.valueobjects
                        .PrescriptionParams.RepLimits.MIN_REPS_BEGINNER : null);

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
                previous.workouts().stream()
                        .map(workout -> new PreviousWorkout(
                                workout.scheduledDate(), workout.focusCode(),
                                workout.workoutStatus(), workout.skipReason(),
                                workout.recorded(), workout.sessionRpe(), workout.completionPct(),
                                previous.prescriptions().stream()
                                        .filter(outcome -> workout.scheduledDate().equals(outcome.scheduledDate()))
                                        .map(outcome -> new PreviousExercise(
                                                outcome.exerciseId(), outcome.name(),
                                                outcome.prescriptionType(),
                                                outcome.sets(), outcome.reps(), outcome.durationSeconds(),
                                                outcome.actualSets(), outcome.actualRepsTotal(),
                                                outcome.actualDurationSeconds(), outcome.actualLoadKg(),
                                                outcome.completionPct(), outcome.exerciseStatus(),
                                                outcome.skipReason()))
                                        .toList()))
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
                        candidate.bodyPartCode(), candidate.equipmentCode(), candidate.difficulty(),
                        candidate.defaultPrescription() == null ? null
                                : candidate.defaultPrescription().name(),
                        candidate.targetMuscle())));

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
                PrescriptionPrinciples.VERSION,
                user, constraints, adjustment, previousWeek, exercises,
                context.safety() == null ? null : context.safety().describe());
    }

    private static Integer minSessionMinutesOrNull(PlanGenerationContext context) {
        int floor = PlanDraftValidator.minimumSessionMinutes(context);
        return floor <= 0 ? null : floor;
    }

    private static List<ReduceVolumeCap> reduceVolumeCapsOrNull(PlanGenerationContext context) {
        var caps = PlanDraftValidator.reductionCaps(context);
        if (caps.isEmpty()) return null;
        return caps.values().stream()
                .map(cap -> new ReduceVolumeCap(cap.exerciseId(), cap.maxSets(), cap.maxReps(), cap.reason()))
                .toList();
    }
}