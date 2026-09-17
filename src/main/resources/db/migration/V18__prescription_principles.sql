-- =====================================================================
-- FitSense MVP 1.0 - V18: principios de prescripcion P-1.0
--
-- Dos cambios, los dos necesarios para que la IA prescriba con criterio
-- propio y coherente (principios P-1.0, basados en ACSM 2026):
--
--   1. Corrige exercises.default_prescription.
--   2. Reemplaza el rango de repeticiones POR OBJETIVO por un limite amplio.
--
-- 1. POR QUE SE CORRIGE default_prescription
-- V9 lo derivo por palabra clave SIN limite de palabra, el mismo error que
-- V10 ya documento para high_impact: "crunch" contiene "run" y quedo como
-- DURATION. Resultado sobre el catalogo activo: 38 ejercicios marcados por
-- duracion cuando van por repeticiones (todos los crunch, "outstretched"
-- por "stretch", "staircase" y otros), y 1 al reves.
--
-- Mientras la IA no recibia el tipo, el error no se veia. Ahora el tipo
-- viaja con cada ejercicio y la validacion 7 lo exige: sin esta correccion
-- el sistema OBLIGARIA a prescribir un crunch en segundos.
--
-- Criterio: DURATION si el grupo es cardio o el nombre indica sosten o
-- desplazamiento (stretch, plank, isometric, hold, wall sit, walk, run, jog,
-- climber). Todo lo demas SETS_REPS. Sigue siendo una heuristica: la revision
-- manual del catalogo la confirma o corrige ejercicio por ejercicio.
--
-- No afecta planes ya generados: planned_workout_exercises guarda su propio
-- prescription_type.
--
-- 2. POR QUE SE QUITA by_goal (configuracion nueva: siguiente MVP-1.N)
-- Un rango por objetivo (INCREASE_STRENGTH = 4-10 para todo) hacia ilegal la
-- prescripcion coherente: 15 elevaciones de talon para un perfil de fuerza
-- se rechazaban. Los rangos ademas no tenian respaldo (problema 9).
--
-- Con P-1.0 las repeticiones las decide la IA por ejercicio, segun esfuerzo
-- (2-3 repeticiones en reserva), nivel, entorno y equipamiento. El backend
-- solo rechaza lo absurdo:
--   min_reps = 6   el piso que ya existia (V9 de 19.3 y ck_pwe_min_reps).
--   max_reps = 30  DECISION DE DISENO PROVISIONAL, sin referencia. Se
--                  reemplaza por limites por categoria de ejercicio cuando
--                  el catalogo este clasificado.
--
-- by_goal se conserva en las versiones anteriores de la configuracion: los
-- planes ya generados siguen ligados a los rangos con los que se validaron.
-- =====================================================================


-- 1. TIPO DE PRESCRIPCION ---------------------------------------------
-- \y es limite de palabra en PostgreSQL. Sin el, se repite el error de V9.

UPDATE exercises e
SET default_prescription = CASE
                               WHEN bp.code = 'cardio'
                                   OR e.name_en ~* '\y(stretch|stretching|plank|planks|isometric|hold|holds|wall sit|walk|walking|run|running|jog|jogging|climber|climbers)\y'
        THEN 'DURATION'
                               ELSE 'SETS_REPS'
    END
    FROM body_parts bp
WHERE bp.body_part_id = e.body_part_id;


-- 2. CONFIGURACION NUEVA ----------------------------------------------
-- Se parte de la configuracion ACTIVA, sea cual sea, y no de un nombre fijo.
-- La primera version de esta migracion asumia que la activa era MVP-1.4 y
-- fallo con ux_config_active: en la base habia otra version activa. Partir de
-- la activa ademas conserva cualquier clave que esa version haya anadido.
-- El nombre nuevo es el siguiente MVP-1.N libre, para no chocar con versiones
-- que ya existan.

DO $$
DECLARE
activas       INTEGER;
    base_version  TEXT;
    base_params   JSONB;
    nueva_version TEXT;
BEGIN
SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
IF activas <> 1 THEN
        RAISE EXCEPTION 'Antes de V18 debe haber exactamente una configuracion activa y hay %', activas;
END IF;

SELECT version, params INTO base_version, base_params
FROM calculation_configs WHERE is_active;

SELECT 'MVP-1.' || (COALESCE(MAX(substring(version FROM '^MVP-1\.(\d+)$')::INTEGER), 0) + 1)
INTO nueva_version
FROM calculation_configs;

-- ux_config_active admite una sola fila activa: desactivar ANTES de insertar.
UPDATE calculation_configs SET is_active = FALSE WHERE is_active;

INSERT INTO calculation_configs (version, description, params, is_active)
VALUES (
           nueva_version,
           'Principios P-1.0 (desde ' || base_version || '): sin rango por objetivo, limite amplio de repeticiones. Provisional.',
           jsonb_set(base_params, '{prescription}',
                     (COALESCE(base_params -> 'prescription', '{}'::jsonb) - 'by_goal')
                         || jsonb_build_object('rep_limits',
                                               jsonb_build_object('min_reps', 6, 'max_reps', 30))),
           TRUE
       );

RAISE NOTICE 'V18: % desactivada, % creada y activa', base_version, nueva_version;
END $$;


-- 3. COMPROBACION -----------------------------------------------------
DO $$
DECLARE
activas          INTEGER;
    tiene_limites    BOOLEAN;
    tiene_by_goal    BOOLEAN;
    crunch_duracion  INTEGER;
    activos_duracion INTEGER;
    activos_reps     INTEGER;
BEGIN
SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;

SELECT (params -> 'prescription') ? 'rep_limits',
        (params -> 'prescription') ? 'by_goal'
INTO tiene_limites, tiene_by_goal
FROM calculation_configs WHERE is_active;

SELECT COUNT(*) INTO crunch_duracion
FROM exercises
WHERE is_active AND default_prescription = 'DURATION' AND name_en ~* '\ycrunch\y';

SELECT COUNT(*) FILTER (WHERE default_prescription = 'DURATION'),
        COUNT(*) FILTER (WHERE default_prescription = 'SETS_REPS')
INTO activos_duracion, activos_reps
FROM exercises WHERE is_active;

IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
    IF NOT tiene_limites THEN
        RAISE EXCEPTION 'La configuracion activa no tiene prescription.rep_limits';
END IF;
    IF tiene_by_goal THEN
        RAISE EXCEPTION 'La configuracion activa todavia tiene prescription.by_goal';
END IF;
    IF crunch_duracion > 0 THEN
        RAISE EXCEPTION '% crunch activos siguen marcados como DURATION', crunch_duracion;
END IF;

    RAISE NOTICE 'V18 aplicada. Activos por duracion: %, por repeticiones: %',
                 activos_duracion, activos_reps;
END $$;