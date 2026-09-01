-- =====================================================================
-- FitSense MVP 1.0 - V10: banderas de seguridad y dificultad
--
-- Dos cosas:
--   1. Anade high_impact, requires_floor y axial_load a exercises.
--   2. Deriva esas banderas y difficulty_level por reglas sobre name_en.
--
-- LA ACTIVACION NO ESTA AQUI. Vive en V11, junto a las traducciones, porque
-- depende de ellas: ck_exercises_active_translated impide activar un ejercicio
-- sin name_es, y los 1.324 del dataset entran por V9 sin traducir.
--
-- Estaban juntas en una sola migracion y fallaba en cualquier entorno nuevo: la
-- comprobacion encontraba 0 activos y abortaba. Separarlas deja a V10 dependiendo
-- solo del esquema, que es lo que Hibernate necesita para arrancar.
--
-- POR QUE LAS BANDERAS
-- El conjunto elegible filtraba por equipamiento, ubicacion, dificultad y
-- bloqueos, pero NO por edad. birth_date viajaba en el prompt y la IA podia
-- tenerla en cuenta o no; nada lo garantizaba. Prescribir saltos a un
-- participante de 68 anos es un riesgo real en un estudio con personas, y
-- ademas contamina el estudio: si el plan no encaja, la adherencia baja y el
-- sistema interpreta esa caida como falta de compromiso, reduciendo volumen
-- cuando el problema era la seleccion.
--
-- Se filtran en la consulta y no solo en el prompt por la misma razon que
-- requires_gym: cuando la consecuencia es una lesion, no se confia en que el
-- modelo obedezca una instruccion en texto.
--
-- ADVERTENCIA METODOLOGICA
-- Las banderas y difficulty_level se derivan por palabras clave sobre el nombre
-- en ingles. Es una heuristica documentada y reproducible, NO una clasificacion
-- clinica. La seccion 8 exige que la dificultad de los ejercicios activados la
-- revisen dos evaluadores con acuerdo inter-jueces reportable. Esta migracion
-- da el punto de partida; la revision sigue pendiente.
-- =====================================================================


-- 1. COLUMNAS ---------------------------------------------------------

ALTER TABLE exercises
    ADD COLUMN high_impact    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requires_floor BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN axial_load     BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN exercises.high_impact IS
    'Saltos, pliometria y levantamientos olimpicos. Se excluye por encima de la edad de corte.';
COMMENT ON COLUMN exercises.requires_floor IS
    'Exige bajar al suelo y levantarse. Barrera de movilidad, no de fuerza.';
COMMENT ON COLUMN exercises.axial_load IS
    'Carga sobre columna o cuello, o posicion invertida.';


-- 2. DERIVACION -------------------------------------------------------
-- Se usa \y (limite de palabra) y no LIKE: sin el, "crunch" contiene "run"
-- y todos los abdominales quedaban marcados como alto impacto.

UPDATE exercises SET high_impact = TRUE
WHERE name_en ~* '\y(jump|jumps|jumping|hop|hops|plyo|plyometric|burpee|burpees|clap|sprint|sprints|skater|depth|bound|slam|snatch|clean|jerk|kipping|muscle-up|star|jack|skip|explosive|dash|thruster|box)\y';

UPDATE exercises SET requires_floor = TRUE
WHERE name_en ~* '\y(lying|floor|supine|prone|plank|crunch|crunches|sit-up|sit-ups|bridge|push-up|push|cobra|superman|mountain|crawl|sphinx|v-up|hollow|kneeling|bug|dog|flutter|rollerout|rollout|jackknife)\y';

UPDATE exercises SET axial_load = TRUE
WHERE name_en ~* '\y(handstand|headstand|pike|inverted|neck|hyperextension|sissy|jefferson|zercher|turkish|deadlift|snatch|jerk|clean|pullover)\y'
   OR name_en ~* 'good morning';


-- 3. DIFICULTAD -------------------------------------------------------
-- Base por equipamiento, mas y menos un nivel por palabras clave. Solo se
-- toca lo que viene del dataset: los MVP- de V8 conservan la suya.

UPDATE exercises e SET difficulty_level = GREATEST(1, LEAST(3,
                                                            CASE
                                                                WHEN eq.code IN ('body weight','band','stability ball','medicine ball') THEN 1
                                                                WHEN eq.code IN ('barbell','olympic barbell','ez barbell','kettlebell',
                                                                                 'weighted','smith machine','sled machine')             THEN 3
                                                                ELSE 2
                                                                END
                                                                + CASE WHEN e.name_en ~* '\y(one|single|pistol|handstand|muscle-up|planche|plyo|jump|archer|turkish|snatch|clean|jerk)\y'
               THEN 1 ELSE 0 END
                                                                - CASE WHEN e.name_en ~* '\y(assisted|kneeling|wall|incline|machine|lever|smith|supported|seated|stretch|isometric|modified)\y'
               THEN 1 ELSE 0 END
                                                      ))
    FROM equipment_types eq
WHERE eq.equipment_id = e.equipment_id
  AND e.source_code NOT LIKE 'MVP-%';



-- 4. COMPROBACION -----------------------------------------------------
DO $$
DECLARE
columnas INTEGER;
BEGIN
SELECT COUNT(*) INTO columnas FROM information_schema.columns
WHERE table_name = 'exercises'
  AND column_name IN ('high_impact', 'requires_floor', 'axial_load');

IF columnas <> 3 THEN
        RAISE EXCEPTION 'Faltan columnas de seguridad: solo se crearon %', columnas;
END IF;

    RAISE NOTICE 'V10 aplicada: banderas de seguridad y dificultad derivadas';
END $$;
