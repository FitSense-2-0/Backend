-- =====================================================================
-- FitSense MVP 1.0 - V10: banderas de seguridad, dificultad y activacion
--
-- Tres cosas, en este orden:
--   1. Anade high_impact, requires_floor y axial_load a exercises.
--   2. Deriva esas banderas y difficulty_level por reglas sobre name_en.
--   3. Activa el subconjunto con media e instrucciones en espanol.
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


-- 4. ACTIVACION -------------------------------------------------------
-- Criterio: equipamiento de casa o de gimnasio comun, con GIF y con
-- instrucciones en espanol. Sin gif la app muestra un hueco; sin
-- instrucciones el participante no sabe como ejecutarlo.
--
-- ck_exercises_active_translated exige name_es, que ya esta completo.

UPDATE exercises e SET is_active = TRUE
    FROM equipment_types eq
WHERE eq.equipment_id = e.equipment_id
  AND e.source_code NOT LIKE 'MVP-%'
  AND eq.code IN ('body weight','dumbbell','band','kettlebell','stability ball',
    'medicine ball','barbell','cable','ez barbell',
    'leverage machine','smith machine')
  AND e.gif_path IS NOT NULL
  AND e.instructions_es IS NOT NULL
  AND e.name_es IS NOT NULL;


-- 5. RETIRAR LA SEMILLA DE V8 -----------------------------------------
-- Se DESACTIVAN, no se borran: planned_workout_exercises tiene una FK hacia
-- exercises y los planes de prueba ya generados apuntan a estos ids. Borrarlos
-- reventaria la migracion, y ademas perderia la trazabilidad de esos planes.

UPDATE exercises SET is_active = FALSE WHERE source_code LIKE 'MVP-%';


-- 6. COMPROBACION -----------------------------------------------------
DO $$
DECLARE
activos      INTEGER;
    sin_nombre   INTEGER;
    principiante INTEGER;
BEGIN
SELECT COUNT(*) INTO activos FROM exercises WHERE is_active;
SELECT COUNT(*) INTO sin_nombre FROM exercises WHERE is_active AND name_es IS NULL;

-- Cobertura minima de un perfil HOME + BEGINNER: si esto sale bajo, ese
-- perfil no podra generar planes y el fallo aparecera semanas despues.
SELECT COUNT(*) INTO principiante
FROM exercises e JOIN equipment_types eq ON eq.equipment_id = e.equipment_id
WHERE e.is_active AND e.difficulty_level = 1
  AND eq.code IN ('body weight','dumbbell','band') AND NOT eq.requires_gym;

IF activos < 500 THEN
        RAISE EXCEPTION 'Solo % ejercicios activos; se esperaban mas de 500', activos;
END IF;
    IF sin_nombre > 0 THEN
        RAISE EXCEPTION '% ejercicios activos sin name_es', sin_nombre;
END IF;
    IF principiante < 100 THEN
        RAISE EXCEPTION 'Solo % ejercicios para HOME+BEGINNER; insuficiente', principiante;
END IF;

    RAISE NOTICE 'V10 aplicada: % activos, % elegibles para HOME+BEGINNER', activos, principiante;
END $$;
