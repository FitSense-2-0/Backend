package main.web.services.fitsense.planning.infrastructure.generation.ai;

import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;
import main.web.services.fitsense.shared.infrastructure.json.JsonSupport;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Arma el prompt. La entrada estructurada de 19.1 viaja como JSON dentro del
 * mensaje, tal cual se persiste en input_snapshot, para que lo que se envio y lo
 * que se guardo sean literalmente lo mismo.
 */
@Component
public class PlanPromptBuilder {

    private final JsonSupport jsonSupport;

    public PlanPromptBuilder(JsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    public String build(PlanGenerationContext context, String inputSnapshotJson,
                        List<String> previousProblems) {
        var prompt = new StringBuilder();

        prompt.append("""
                Eres un entrenador que diseña planes semanales de entrenamiento.
                Devuelve UNICAMENTE el objeto JSON del esquema, sin texto adicional.

                Reglas que el backend verifica y que invalidan tu propuesta si no se cumplen:
                1.  Usa solo exercise_id presentes en available_exercises. No inventes ninguno.
                2.  Genera exactamente tantos entrenamientos como days_per_week.
                3.  Programa solo en fechas cuyo dia de la semana este en available_days,
                    dentro del rango week_start_date a week_end_date, formato AAAA-MM-DD.
                4.  expected_duration_minutes no puede superar session_minutes mas 15 %.
                5.  Cada entrenamiento lleva al menos 2 ejercicios.
                6.  Ningun ejercicio puede superar max_difficulty_level.
                7.  prescription_type SETS_REPS exige planned_sets y planned_reps.
                    prescription_type DURATION exige planned_duration_seconds.
                8.  Si hay adjustment, la suma de volumen debe caer entre target_volume_min
                    y target_volume_max. Volumen = planned_sets x planned_reps, o
                    planned_duration_seconds x planned_sets / 30 para los de duracion.
                    La carga NO cuenta como volumen.
                9.  Minimo 2 series y 6 repeticiones por ejercicio.
                10. Minimo 20 segundos en los ejercicios de duracion.
                11. Si adjustment.types incluye LOWER_LOAD, deja target_load_kg en null.
                12. No pongas dos entrenamientos el mismo dia.
                13. No repitas el mismo focus_code en dias consecutivos.

                total_volume debe ser la suma real de tus prescripciones: el backend la
                recalcula y rechaza la propuesta si no coincide.

                rationale se le muestra al usuario para explicarle el cambio: escribelo en
                espanol, en segunda persona, breve y concreto.
                """);

        if (!previousProblems.isEmpty()) {
            // Segundo intento: la lista de incumplimientos es lo unico que
            // distingue este intento del anterior (19.4).
            prompt.append("\nTu propuesta anterior fue rechazada por estos motivos. Corrigelos:\n");
            previousProblems.forEach(problem -> prompt.append("- ").append(problem).append('\n'));
        }

        prompt.append("\nDatos de entrada:\n").append(inputSnapshotJson);
        return prompt.toString();
    }
}
