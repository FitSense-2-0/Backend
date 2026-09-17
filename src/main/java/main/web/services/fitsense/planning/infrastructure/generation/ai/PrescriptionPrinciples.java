package main.web.services.fitsense.planning.infrastructure.generation.ai;

/**
 * Principios de prescripcion que la IA aplica a cada ejercicio.
 * <p>
 * Base: Currier et al. (2026), ACSM Position Stand, Med Sci Sports Exerc
 * 58(4):851-872. Las lineas marcadas [Diseno] NO son recomendaciones del ACSM:
 * son criterios de operacionalizacion de FitSense y se declaran como tales en
 * el metodo.
 * <p>
 * EL TEXTO ES PARTE DE LA INTERVENCION. Se congela antes del primer
 * participante. Cualquier cambio exige subir VERSION: la version viaja en
 * input_snapshot, asi que cada plan queda ligado al texto con que se genero.
 * <p>
 * Las marcas [ACSM] y [Diseno] se quitan del texto que recibe el modelo: son
 * trazabilidad para la tesis, no instrucciones, y solo agregarian ruido.
 */
public final class PrescriptionPrinciples {

    /**
     * P-1.0: puntos 1-8. P-1.1: anade el punto 9, desempeno de la semana
     * anterior. P-1.2: el punto 8 pasa de "sin kilos" a peso sugerido con tope.
     */
    public static final String VERSION = "P-1.2";

    /*
     * Trazabilidad por punto (no se envia al modelo):
     *  1 Esfuerzo 2-3 RIR ........................ [ACSM, operacionalizacion practica]
     *  2 Repeticiones segun el ejercicio .......... [ACSM + Diseno]
     *  3 Fuerza / musculo ......................... [ACSM]
     *    Perder peso / general .................... [Diseno]
     *  4 Rango completo, movimiento controlado .... [ACSM]
     *  5 BEGINNER ................................. [Diseno]
     *    INTERMEDIATE / ADVANCED .................. [ACSM + Diseno]
     *  6 Entorno y herramientas ................... [Diseno]
     *  7 Tipo de prescripcion ..................... [Diseno]
     *  8 Peso sugerido: tope +10 % ................. [ACSM 2009]
     *    Solo con peso anotado, todo cumplido,
     *    una variable a la vez, saltos de pesa ..... [Diseno]
     *  9 Uso del desempeno previo ................. [Diseno]
     *    Umbrales de RPE (8 y 5) .................. [Diseno, sin calibrar]
     *    ADVERTENCIA: el RPE aun no esta instrumentado (escala sin anclajes ni
     *    familiarizacion, ver progressionRpeCeiling). Por eso va como senal
     *    secundaria al cumplimiento, nunca como criterio principal.
     */
    static final String TEXT = """
            PRINCIPIOS DE PRESCRIPCION (version P-1.2). Aplicalos a CADA ejercicio.

            1. ESFUERZO. No hace falta llegar al fallo. Elige las repeticiones para
               que la persona termine cada serie con unas 2-3 repeticiones en reserva.

            2. LAS REPETICIONES DEPENDEN DEL EJERCICIO, no de un numero fijo. Un
               ejercicio pequeno o facil (gemelos, abdominales) necesita mas
               repeticiones para ser exigente que uno grande o dificil (dominadas,
               sentadilla bulgara). No repitas la misma prescripcion en todo el plan
               por comodidad.

            3. OBJETIVO (user.goal_type).
               - INCREASE_STRENGTH: 2-3 series, ejercicio prioritario al inicio de la
                 sesion. Repeticiones bajas solo si la persona puede ajustar la carga.
               - GAIN_MUSCLE: sirven rangos amplios si el esfuerzo es suficiente.
                 Orientacion: unas 10 series por grupo muscular a la semana, si la
                 sesion lo permite.
               - LOSE_WEIGHT o GENERAL_FITNESS: rangos moderados; prioriza que la
                 persona pueda sostener el plan.

            4. EJECUCION. Rango de movimiento completo cuando el ejercicio lo permita,
               movimiento controlado.

            5. NIVEL (user.fitness_level).
               - BEGINNER: ejercicios simples, 2 series, repeticiones moderadas o
                 altas, unas 3 en reserva, prioridad a la tecnica.
               - INTERMEDIATE: 2-3 series, 2-3 en reserva.
               - ADVANCED: 2-3 o mas series segun el volumen semanal.

            6. ENTORNO Y HERRAMIENTAS (constraints.training_location y
               user.equipment_codes).
               - GYM: prescribe repeticiones; la persona elige el peso con el que
                 llegue a ellas dejando 2-3 en reserva.
               - Casa con peso corporal: si la variante es facil, elige una mas
                 dificil (unilateral, mas palanca) o sube repeticiones. Nunca bajes
                 repeticiones para "hacerla de fuerza".
               - Ligas (band): la tension se regula con la liga; repeticiones medias.
               - Mancuernas o pesas rusas en casa: asume peso fijo. Si queda facil,
                 sube repeticiones o usa una variante unilateral.

            7. TIPO DE PRESCRIPCION. Usa el prescription_type que trae cada ejercicio
               en available_exercises: DURATION va por segundos, SETS_REPS por
               repeticiones. No lo cambies.

            8. PESO SUGERIDO (target_load_kg). Es solo una sugerencia para la persona
               y solo en ejercicios SETS_REPS. Nunca inventes kilos.
               - Si ese ejercicio no tiene actual_load_kg en previous_week, dejalo en
                 null.
               - Puedes repetir el peso que uso o sugerir menos.
               - Sugiere mas peso solo si hizo todas las repeticiones pedidas, como
                 maximo un 10 % sobre el peso que uso, y sin subir series ni
                 repeticiones de ese ejercicio.
               - Si la siguiente pesa sube mas de un 10 % (por ejemplo de 8 a 10 kg),
                 manten el peso y sube repeticiones.
               - Si ese dia tuvo session_rpe de 8 o mas, no subas el peso.
               - Si adjustment.types incluye LOWER_LOAD, no subas ningun peso.

            9. DESEMPENO DE LA SEMANA ANTERIOR (previous_week.workouts). Usalo para
               decidir DONDE y COMO ajustar cada ejercicio. CUANTO cambia el volumen
               total lo fija adjustment; no lo cambies por tu cuenta.
               - exercise_status COMPLETED: puedes mantenerlo o progresarlo.
               - PARTIAL: baja repeticiones o usa una variante mas facil del mismo
                 grupo muscular.
               - SKIPPED con PAIN_OR_DISCOMFORT: no lo repitas; elige otro de la
                 misma zona, mas conservador.
               - SKIPPED con TOO_DIFFICULT o FATIGUE: variante mas facil.
               - SKIPPED con LACK_OF_TIME, SCHEDULE_CHANGE o LACK_OF_MOTIVATION: no
                 bajes la dificultad por eso.
               - NOT_RECORDED o recorded false: no hay datos. No lo interpretes como
                 fracaso ni como exito.
               - session_rpe es el esfuerzo de TODO el dia (1-10) y es una senal
                 secundaria: el cumplimiento manda. Con 8 o mas, no progreses los
                 ejercicios de ese dia aunque se completaran. Con 5 o menos y todo
                 completo, puedes progresarlos.
               - actual_load_kg es el peso que uso la persona: base del peso
                 sugerido (principio 8).
            """;

    private PrescriptionPrinciples() {}
}