package main.web.services.fitsense.planning.infrastructure.generation.ai;

import java.util.List;
import java.util.Map;

/**
 * Esquema JSON con el que Replicate fuerza la forma de la salida (19.2).
 * <p>
 * Se usa el modelo openai/gpt-5-structured en vez de openai/gpt-5-nano directo
 * porque acepta json_schema. La diferencia importa para la tesis: pedir JSON por
 * prompt a un modelo pequeno falla a menudo por formato (bloques de codigo,
 * numeros como texto, campos de mas), y cada fallo empujaria al motor de reglas.
 * La proporcion RULE_ENGINE frente a AI es un resultado reportable (20.6), asi
 * que conviene que mida la capacidad del modelo para adaptar planes y no la
 * robustez del parser.
 */
final class PlanJsonSchema {

    private PlanJsonSchema() {}

    static Map<String, Object> format() {
        return Map.of("format", Map.of(
                "type", "json_schema",
                "name", "weekly_training_plan",
                "schema", schema()));
    }

    private static Map<String, Object> schema() {
        return object(
                Map.of(
                        "schema_version", type("string"),
                        "plan_name", type("string"),
                        "total_volume", type("integer"),
                        "rationale", type("string"),
                        "workouts", Map.of("type", "array", "items", workout())),
                List.of("schema_version", "plan_name", "total_volume", "rationale", "workouts"));
    }

    private static Map<String, Object> workout() {
        return object(
                Map.of(
                        "scheduled_date", type("string"),
                        "focus_code", enumOf("FULL_BODY", "UPPER_BODY", "LOWER_BODY",
                                "PUSH", "PULL", "LEGS", "CORE"),
                        "workout_name", type("string"),
                        "expected_duration_minutes", type("integer"),
                        "exercises", Map.of("type", "array", "items", exercise())),
                List.of("scheduled_date", "focus_code", "workout_name",
                        "expected_duration_minutes", "exercises"));
    }

    private static Map<String, Object> exercise() {
        // Los campos opcionales se declaran nullable en vez de omitirse de
        // "required": el modo estricto de OpenAI exige que required liste todas
        // las propiedades, asi que la opcionalidad se expresa con el tipo.
        return object(
                Map.of(
                        "exercise_id", type("integer"),
                        "exercise_order", type("integer"),
                        "prescription_type", enumOf("SETS_REPS", "DURATION"),
                        "planned_sets", nullable("integer"),
                        "planned_reps", nullable("integer"),
                        "planned_duration_seconds", nullable("integer"),
                        "target_load_kg", nullable("number"),
                        "rest_seconds", nullable("integer"),
                        "notes", nullable("string")),
                List.of("exercise_id", "exercise_order", "prescription_type", "planned_sets",
                        "planned_reps", "planned_duration_seconds", "target_load_kg",
                        "rest_seconds", "notes"));
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false);
    }

    private static Map<String, Object> type(String type) {
        return Map.of("type", type);
    }

    private static Map<String, Object> nullable(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static Map<String, Object> enumOf(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }
}
