-- =====================================================================
-- FitSense MVP 1.0 - V20: coherencia de prescripcion (minimos por zona y
-- descanso maximo)
--
-- POR QUE
-- Con la banda de volumen correcta, la IA puso los 12 ejercicios de la semana
-- a 3x10: gemelos, abdominales, press, remo y sentadilla. Cumplia todas las
-- validaciones, porque el limite 6-30 no distingue un gemelo de una dominada,
-- y la instruccion "no pongas todo igual" no se verifica. El modelo obedece lo
-- que se valida y omite lo que no.
--
-- QUE ANADE (sobre MVP-1.6, por nombre: nunca se parte de la activa a ciegas,
-- que fue el error que contamino MVP-1.5 con TEST-FAIL)
--
--   prescription.rep_limits.min_reps_by_body_part = {lower legs: 12, waist: 10}
--       DECISION DE DISENO PROVISIONAL, sin referencia. Aproxima la exigencia
--       por ejercicio usando body_part, el unico dato disponible, hasta que el
--       catalogo este clasificado por categoria de movimiento. Solo SETS_REPS:
--       una plancha (DURATION) no se ve afectada.
--
--   prescription.max_rest_seconds = 180
--       Tope de los 2-3 min que ACSM (2009) indica para multiarticulares
--       pesados. Evita que la duracion minima (validacion 20, que usa el
--       session_minutes_floor_pct = 70 ya existente desde V12) se cumpla
--       inflando descansos.
-- =====================================================================

DO $$
DECLARE
base_params   JSONB;
    activas       INTEGER;
    nueva_version TEXT;
    activa_version TEXT;
BEGIN
SELECT params INTO base_params FROM calculation_configs WHERE version = 'MVP-1.6';
IF base_params IS NULL THEN
        RAISE EXCEPTION 'No existe MVP-1.6: aplica V19 antes que V20';
END IF;

SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
SELECT version INTO activa_version FROM calculation_configs WHERE is_active;
IF activa_version <> 'MVP-1.6' THEN
        RAISE NOTICE 'V20: la activa es % y no MVP-1.6; se reemplaza igualmente por la construida desde MVP-1.6',
            activa_version;
END IF;

    base_params := jsonb_set(base_params, '{prescription,rep_limits,min_reps_by_body_part}',
                             jsonb_build_object('lower legs', 12, 'waist', 10), true);
    base_params := jsonb_set(base_params, '{prescription,max_rest_seconds}', to_jsonb(180), true);

SELECT 'MVP-1.' || (COALESCE(MAX(substring(version FROM '^MVP-1\.(\d+)$')::INTEGER), 0) + 1)
INTO nueva_version FROM calculation_configs;

UPDATE calculation_configs SET is_active = FALSE WHERE is_active;

INSERT INTO calculation_configs (version, description, params, is_active)
VALUES (nueva_version,
        'MVP-1.6 + minimos provisionales por body_part (lower legs 12, waist 10) y descanso maximo 180 s.',
        base_params, TRUE);

RAISE NOTICE 'V20: % creada y activa (desde MVP-1.6)', nueva_version;
END $$;


-- COMPROBACION --------------------------------------------------------
DO $$
DECLARE
gemelos    INTEGER;
    abdomen    INTEGER;
    descanso   INTEGER;
    tolerancia NUMERIC;
BEGIN
SELECT (params #>> '{prescription,rep_limits,min_reps_by_body_part,lower legs}')::INTEGER,
        (params #>> '{prescription,rep_limits,min_reps_by_body_part,waist}')::INTEGER,
        (params #>> '{prescription,max_rest_seconds}')::INTEGER,
        (params #>> '{adjustment,volume_tolerance_pct}')::NUMERIC
INTO gemelos, abdomen, descanso, tolerancia
FROM calculation_configs WHERE is_active;

IF gemelos IS DISTINCT FROM 12 OR abdomen IS DISTINCT FROM 10 THEN
        RAISE EXCEPTION 'min_reps_by_body_part no quedo en lower legs 12 / waist 10';
END IF;
    IF descanso IS DISTINCT FROM 180 THEN
        RAISE EXCEPTION 'max_rest_seconds no quedo en 180';
END IF;
    IF tolerancia IS DISTINCT FROM 5.0 THEN
        RAISE EXCEPTION 'volume_tolerance_pct deberia seguir en 5.0 y es %', tolerancia;
END IF;
END $$;