package main.web.services.fitsense.shared.domain.model.valueobjects;

/**
 * Lenguaje publicado del sistema. Vive en el kernel compartido, no en un
 * contexto, porque las mismas siete causas aparecen en cuatro tablas
 * (planned_workouts, workout_session_exercises, weekly_user_metrics,
 * user_interventions) con el mismo CHECK.
 * <p>
 * Es el insumo del modificador de la seccion 18.3: permite que la respuesta del
 * sistema dependa de la causa y no solo del porcentaje.
 */
public enum SkipReason {
    LACK_OF_TIME,
    FATIGUE,
    LACK_OF_MOTIVATION,
    TOO_DIFFICULT,
    PAIN_OR_DISCOMFORT,
    SCHEDULE_CHANGE,
    OTHER
}
