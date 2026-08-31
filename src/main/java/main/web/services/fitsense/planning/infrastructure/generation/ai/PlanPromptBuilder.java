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
                14. Si safety_notes no es null, respetalo al prescribir.
                    available_exercises YA excluye lo prohibido, pero de los que
                    quedan elige y pauta las variantes mas conservadoras: menos
                    rango de movimiento, menos series, mas descanso.
                15. Cada entrenamiento debe CUBRIR su enfoque, no repetir zona.
                    Un FULL_BODY con seis ejercicios de biceps no es cuerpo
                    completo. Cubre al menos 3 body_part distintos de los que el
                    enfoque admite, y que ningun grupo se lleve mas de la mitad
                    de la sesion.

                available_exercises viene agrupado por body_part y mezclado dentro
                de cada grupo: NO tomes los primeros de la lista. Lee el body_part
                de cada uno y elige a proposito.

                Y elige pensando en el objetivo del participante (user.goal_type y
                user.goal_text), no solo en lo que cabe: no es lo mismo preparar a
                alguien que quiere perder peso que a quien busca fuerza maxima.

                Un plan que el participante no puede hacer no es solo un plan
                malo: produce un dato falso. Si no encaja con el, su adherencia
                bajara y el sistema lo interpretara como falta de compromiso.
                
                Grupos que admite cada focus_code. Elige el foco ANTES de elegir
                los ejercicios, y no metas grupos que ese foco no admite:
                  FULL_BODY   chest, back, upper legs, shoulders, waist
                  UPPER_BODY  chest, back, shoulders, upper arms
                  LOWER_BODY  upper legs, lower legs, waist
                  PUSH        chest, shoulders, upper arms
                  PULL        back, upper arms
                  LEGS        upper legs, lower legs
                  CORE        waist

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
