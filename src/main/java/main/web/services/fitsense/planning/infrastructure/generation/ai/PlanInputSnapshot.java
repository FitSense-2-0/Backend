package main.web.services.fitsense.planning.infrastructure.generation.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;
import main.web.services.fitsense.planning.domain.model.valueobjects.TargetPrescription;
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
        /**
         * Version del texto de reglas del prompt (PlanPromptBuilder.VERSION).
         * GEN-IN-1.6. Junto a principles_version y el modelo, fija con que
         * instrucciones se genero cada plan: es lo que se congela antes del
         * primer participante.
         */
        @JsonProperty("prompt_version") String promptVersion,
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
            /**
             * GEN-IN-1.6: solo el maximo. El minimo viaja ya calculado en cada
             * ejercicio (available_exercises[].min_reps). Antes la IA tenia que
             * cruzar body_part con min_reps_by_body_part y con
             * min_reps_for_level y quedarse con el mayor; en el plan 29 tomo el 8
             * del nivel para gemelos y abdomen y fallo V16 en los dos intentos.
             * Con min_reps por ejercicio (plan 30) ese fallo desaparecio.
             */
            @JsonProperty("max_reps") Integer maxReps,
            /**
             * GEN-IN-1.7: prescripcion de partida del objetivo (TargetPrescription),
             * la misma que usa el motor de reglas. NO es un tope: es la referencia
             * de la que apartarse por ejercicio. Sin esto, en el plan 30 la IA tomo
             * min_reps como valor por defecto y puso 3x8 a los 21 ejercicios.
             */
            @JsonProperty("target_reps") Integer targetReps,
            @JsonProperty("target_sets") Integer targetSets,
            /** V20: minutos minimos por sesion. null cuando hay orden de volumen. */
            @JsonProperty("min_session_minutes") Integer minSessionMinutes,
            /**
             * GEN-IN-1.7: cuantos ejercicios necesita una sesion para llegar a
             * min_session_minutes con target_sets x target_reps y el descanso de
             * target. Calculado con la misma formula de V17 y V20. La IA no estima
             * bien la duracion: en el plan 30 declaro 21 minutos donde el minimo era
             * 31. null cuando no hay minimo de minutos.
             */
            @JsonProperty("min_exercises_per_session") Integer minExercisesPerSession,
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
            @JsonProperty("reduce_volume_caps") List<ReduceVolumeCap> reduceVolumeCaps) {}

    public record ReduceVolumeCap(
            @JsonProperty("exercise_id") long exerciseId,
            @JsonProperty("max_sets") int maxSets,
            @JsonProperty("max_reps") int maxReps,
            /** COMPLETED: puede progresar un poco. HOLD: no puede subir. */
            String reason) {}

    public record SuggestedSession(
            @JsonProperty("scheduled_date") LocalDate scheduledDate,
            @JsonProperty("focus_code") String focusCode,
            /**
             * GEN-IN-1.8: los body_part que admite ese enfoque, ya resueltos. La
             * IA no tiene que consultar la tabla del prompt.
             */
            @JsonProperty("body_parts") List<String> bodyParts,
            /** Cuantos body_part distintos debe cubrir la sesion como minimo (V15). */
            @JsonProperty("min_body_parts") Integer minBodyParts,
            /**
             * GEN-IN-1.8: tope de ejercicios por body_part en esa sesion, la mitad
             * de min_exercises_per_session (V15). null cuando la regla de la mitad
             * no aplica. Sin este numero, en los planes 31 y 32 el dia inferior
             * salio con 9 de 9 ejercicios en upper legs.
             */
            @JsonProperty("max_per_body_part") Integer maxPerBodyPart) {}

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
            @JsonProperty("target_muscle") String targetMuscle,
            /**
             * GEN-IN-1.6: minimo de repeticiones de ESTE ejercicio, el mismo
             * numero que verifica V16 (RepLimits.minRepsFor con zona y nivel).
             * null en los de duracion o sin limites en la configuracion.
             */
            @JsonProperty("min_reps") Integer minReps) {}

    public static PlanInputSnapshot of(PlanGenerationContext context) {
        var profile = context.profile();

        var user = new User(profile.age(), profile.biologicalSex(), profile.heightCm(),
                profile.weightKg(), profile.targetWeightKg(), profile.fitnessLevel(),
                profile.goalType(), profile.goalText(), profile.healthNotes(),
                profile.equipmentCodes() == null ? List.of() : List.copyOf(profile.equipmentCodes()));

        var limites = context.prescription() == null ? null : context.prescription().repLimits();
        var objetivo = TargetPrescription.forGoal(profile.goalType());
        var minExercises = minExercisesOrNull(context, objetivo);

        var constraints = new Constraints(context.weekNumber(), context.weekStartDate(),
                context.weekEndDate(), context.effectiveDaysPerWeek(), profile.availableDays(),
                context.effectiveSessionMinutes(), profile.trainingLocation(),
                context.effectiveMaxDifficulty(),
                limites == null || !limites.isComplete() ? null : limites.maxReps(),
                (int) objetivo.reps(), (int) objetivo.setsFor(profile.fitnessLevel()),
                minSessionMinutesOrNull(context),
                minExercises,
                context.prescription() == null ? null : context.prescription().maxRestSecondsOrDefault(),
                context.suggestedSplit().stream()
                        .map(session -> suggestedSession(session, minExercises))
                        .toList(),
                reduceVolumeCapsOrNull(context));

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
                        candidate.targetMuscle(),
                        minRepsOrNull(candidate, limites, profile.fitnessLevel()))));

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
                PrescriptionPrinciples.VERSION, PlanPromptBuilder.VERSION,
                user, constraints, adjustment, previousWeek, exercises,
                context.safety() == null ? null : context.safety().describe());
    }

    /**
     * Cuotas por grupo de una sesion, con los mismos numeros que verifica V15:
     * cubrir al menos 2 body_part (o menos, si el enfoque o la sesion no dan) y
     * ninguno por encima de la mitad de los ejercicios. El tope se calcula sobre
     * min_exercises_per_session; si la IA pone mas ejercicios, V15 admitira algo
     * mas, nunca menos.
     */
    private static SuggestedSession suggestedSession(
            main.web.services.fitsense.planning.domain.services.WeeklySplitPlanner.PlannedSession session,
            Integer minExercises) {
        var grupos = session.focus().bodyPartCodes();
        Integer tope = (minExercises == null || grupos.size() < 3 || minExercises < 4)
                ? null : minExercises / 2;
        int minGrupos = minExercises == null
                ? Math.min(2, grupos.size())
                : Math.min(2, Math.min(grupos.size(), minExercises));
        return new SuggestedSession(session.date(), session.focus().name(),
                List.copyOf(grupos), minGrupos, tope);
    }

    /**
     * Ejercicios necesarios para llegar al minimo de minutos con la prescripcion
     * de partida, despejando la formula del SessionDurationEstimator:
     * calentamiento + n x (trabajo + descanso) + (n - 1) x transicion >= minimo.
     * Es una referencia: si la IA usa mas series o mas repeticiones, necesitara
     * menos ejercicios.
     */
    private static Integer minExercisesOrNull(PlanGenerationContext context,
                                              TargetPrescription objetivo) {
        var minMinutos = minSessionMinutesOrNull(context);
        var params = context.prescription();
        if (minMinutos == null || params == null) return null;

        int series = objetivo.setsFor(context.profile().fitnessLevel());
        int porEjercicio = series * objetivo.reps() * params.secondsPerRepOrDefault()
                + Math.max(0, series - 1) * objetivo.restSeconds();
        int transicion = params.transitionSecondsOrDefault();
        if (porEjercicio + transicion <= 0) return null;

        int restante = minMinutos * 60 - params.warmupMinutesOrDefault() * 60 + transicion;
        return Math.max(2, (int) Math.ceil(restante / (double) (porEjercicio + transicion)));
    }

    /** Mismo calculo que V16: sin limites completos en la configuracion, V16 no valida. */
    private static Integer minRepsOrNull(
            main.web.services.fitsense.planning.domain.model.valueobjects.CandidateExercise candidate,
            main.web.services.fitsense.configuration.domain.model.valueobjects.PrescriptionParams.RepLimits limites,
            String fitnessLevel) {
        if (limites == null || !limites.isComplete()) return null;
        if (candidate.defaultPrescription()
                == main.web.services.fitsense.planning.domain.model.valueobjects.PrescriptionType.DURATION)
            return null;
        return limites.minRepsFor(candidate.bodyPartCode(), fitnessLevel);
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