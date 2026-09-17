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
                7.  Usa el prescription_type que trae cada ejercicio en
                    available_exercises; el backend rechaza otro distinto.
                    SETS_REPS exige planned_sets y planned_reps.
                    DURATION exige planned_duration_seconds.
                8.  Si adjustment.target_volume no es null, la suma de volumen debe caer entre target_volume_min
                    y target_volume_max. Volumen = planned_sets x planned_reps, o
                    planned_duration_seconds x planned_sets / 30 para los de duracion.
                    La carga NO cuenta como volumen.
                9.  Minimo 2 series y 6 repeticiones por ejercicio.
                10. Minimo 20 segundos en los ejercicios de duracion.
                11. target_load_kg sigue el principio 8. Sin peso anotado la semana
                    anterior va en null; nunca sube mas de un 10 % sobre el peso
                    usado, y solo si hizo todas las repeticiones.
                12. No pongas dos entrenamientos el mismo dia.
                13. No repitas el mismo focus_code en dias consecutivos. Usa las
                    fechas y enfoques de constraints.suggested_split: ya cumplen
                    esta regla y la 18. Si propones otra division, debe cumplirlas.
                14. Si safety_notes no es null, respetalo al prescribir.
                    available_exercises YA excluye lo prohibido, pero de los que
                    quedan elige y pauta las variantes mas conservadoras: rango de
                    movimiento comodo y sin dolor, menos series, mas descanso.
                15. Cada entrenamiento debe CUBRIR su enfoque, no repetir zona.
                    Un FULL_BODY con seis ejercicios de biceps no es cuerpo
                    completo. Cubre al menos 2 body_part distintos de los que el
                    enfoque admite (3 si la sesion lo permite), y que ningun
                    grupo se lleve mas de la mitad de la sesion.
                16. Las repeticiones las decides tu, ejercicio por ejercicio,
                    con los PRINCIPIOS DE PRESCRIPCION de mas abajo. El backend
                    verifica constraints.rep_limits: ningun planned_reps por
                    encima de max_reps ni por debajo del minimo de su body_part
                    (min_reps_by_body_part; si no aparece, min_reps). Ese limite
                    NO es un objetivo: es solo el borde de lo absurdo. No pongas
                    la misma prescripcion a todos los ejercicios.
                17. expected_duration_minutes debe ser lo que dura el contenido
                    que prescribes, no una copia de session_minutes. El backend
                    lo recalcula asi y rechaza un desvio mayor al 20 %:
                      minutos = 5 de calentamiento
                              + por ejercicio: series x repeticiones x 3 s
                                               (o series x segundos si es DURATION)
                              + por ejercicio: (series - 1) x descanso
                              + 1 minuto de transicion entre ejercicios
                    Declara lo que de verdad dura. Inflarlo NO ayuda: distorsiona
                    la medicion de adherencia y invalida el plan igual.
                    Si constraints.min_session_minutes no es null, cada sesion
                    debe durar al menos eso: la persona reservo ese tiempo. Llega
                    con ejercicios o series, no con descanso: rest_seconds nunca
                    supera constraints.max_rest_seconds.
                18. Recuperacion entre dias consecutivos: el mismo exercise_id
                    no se repite en dos dias seguidos, y chest, back, shoulders y
                    upper legs no se trabajan dos dias seguidos. Con dias
                    seguidos alterna tren superior e inferior (o empuje y
                    traccion). waist, upper arms y lower legs si pueden repetirse.
                19. Si adjustment.types incluye REDUCE_VOLUME, cada ejercicio de
                    constraints.reduce_volume_caps no puede superar su max_sets ni
                    su max_reps. reason HOLD: la persona no lo completo o le costo;
                    no lo subas. reason COMPLETED: lo hizo bien; puede progresar
                    un poco, hasta el tope. El volumen total igual debe bajar: si
                    te falta volumen, anade ejercicios nuevos del enfoque en vez de
                    subir los repetidos. Antes de responder, revisa uno por uno los
                    exercise_id de esa lista que uses.
                20. En PUSH, de upper arms solo ejercicios de triceps; en PULL, solo
                    de biceps. Mira target_muscle de cada ejercicio.
                21. Si constraints.min_reps_for_level no es null, ningun planned_reps
                    baja de ese valor (ademas del minimo de su body_part).
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

                rationale se le muestra al usuario: escribelo en espanol, en segunda persona,
                breve y concreto. Si adjustment.target_volume no es null, di con
                claridad si el volumen sube, baja o se mantiene y por que (usa
                adjustment.reason). Nunca digas que se mantiene si baja o sube.
                """);

        // Los principios van despues de las reglas verificables y antes de los
        // datos: primero lo que invalida el plan, luego el criterio para elegir
        // dentro de lo valido. El texto y su version viven en
        // PrescriptionPrinciples; la version queda en input_snapshot.
        prompt.append('\n').append(PrescriptionPrinciples.TEXT);

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