-- =====================================================================
-- FitSense MVP 1.0 - V19: reconstruir la configuracion activa desde MVP-1.4
--
-- POR QUE
-- V18 creo MVP-1.5 copiando la configuracion que estuviera ACTIVA. En la base
-- de desarrollo la activa era TEST-FAIL, una configuracion de prueba con
-- volume_tolerance_pct = 0. MVP-1.5 heredo ese 0 y la banda de volumen quedo
-- en +-1 repeticion (366-368): la IA tuvo que cuadrar una suma casi exacta y
-- sacrifico la coherencia (repeticiones sueltas de 13, 16, 14 y ejercicios
-- quitados en una semana de reduccion).
--
-- QUE HACE
-- Construye la configuracion desde MVP-1.4, la ultima creada por migraciones
-- y por lo tanto conocida, y le aplica SOLO los cambios de V18:
--   - prescription.by_goal eliminado
--   - prescription.rep_limits = {min_reps: 6, max_reps: 30}
-- Nada se hereda de la configuracion activa. Antes de reemplazarla, informa
-- cada parametro en que la activa difiere de la reconstruida, para dejar
-- constancia de lo que se descarta.
--
-- MVP-1.5 y TEST-FAIL NO se borran: los planes ya generados siguen ligados a
-- la version con la que se produjeron.
-- =====================================================================

DO $$
DECLARE
activas        INTEGER;
    base_params    JSONB;
    activa_version TEXT;
    activa_params  JSONB;
    nuevos_params  JSONB;
    nueva_version  TEXT;
    seccion        TEXT;
    clave          TEXT;
    valor_activa   JSONB;
    valor_nuevo    JSONB;
    diferencias    INTEGER := 0;
BEGIN
SELECT params INTO base_params FROM calculation_configs WHERE version = 'MVP-1.4';
IF base_params IS NULL THEN
        RAISE EXCEPTION 'No existe MVP-1.4: no hay base conocida para reconstruir la configuracion';
END IF;

SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
SELECT version, params INTO activa_version, activa_params
FROM calculation_configs WHERE is_active;

nuevos_params := jsonb_set(base_params, '{prescription}',
            (COALESCE(base_params -> 'prescription', '{}'::jsonb) - 'by_goal')
                || jsonb_build_object('rep_limits',
                                      jsonb_build_object('min_reps', 6, 'max_reps', 30)));

    -- Constancia de lo que se descarta de la activa (comparacion por seccion y clave).
FOR seccion IN SELECT DISTINCT k FROM (
                                          SELECT jsonb_object_keys(activa_params) AS k
                                          UNION SELECT jsonb_object_keys(nuevos_params)) s
    LOOP
        IF jsonb_typeof(activa_params -> seccion) = 'object'
           AND jsonb_typeof(nuevos_params -> seccion) = 'object' THEN
            FOR clave IN SELECT DISTINCT k FROM (
                                                        SELECT jsonb_object_keys(activa_params -> seccion) AS k
                                                        UNION SELECT jsonb_object_keys(nuevos_params -> seccion)) c
    LOOP
                valor_activa := activa_params -> seccion -> clave;
valor_nuevo  := nuevos_params -> seccion -> clave;
                IF valor_activa IS DISTINCT FROM valor_nuevo THEN
                    diferencias := diferencias + 1;
                    RAISE NOTICE 'V19: %.% en % era % y queda %',
                        seccion, clave, activa_version, valor_activa, valor_nuevo;
END IF;
END LOOP;
        ELSIF (activa_params -> seccion) IS DISTINCT FROM (nuevos_params -> seccion) THEN
            diferencias := diferencias + 1;
            RAISE NOTICE 'V19: seccion % en % difiere y se reemplaza', seccion, activa_version;
END IF;
END LOOP;

SELECT 'MVP-1.' || (COALESCE(MAX(substring(version FROM '^MVP-1\.(\d+)$')::INTEGER), 0) + 1)
INTO nueva_version FROM calculation_configs;

UPDATE calculation_configs SET is_active = FALSE WHERE is_active;

INSERT INTO calculation_configs (version, description, params, is_active)
VALUES (nueva_version,
        'MVP-1.4 + principios P-1.0 (rep_limits 6-30, sin by_goal). Reconstruida en V19 '
            || 'porque ' || activa_version || ' heredo parametros de TEST-FAIL.',
        nuevos_params, TRUE);

RAISE NOTICE 'V19: % desactivada (% diferencias), % creada y activa',
        activa_version, diferencias, nueva_version;
END $$;


-- COMPROBACION --------------------------------------------------------
DO $$
DECLARE
tolerancia NUMERIC;
    tiene_limites BOOLEAN;
    tiene_by_goal BOOLEAN;
BEGIN
SELECT (params -> 'adjustment' ->> 'volume_tolerance_pct')::NUMERIC,
        (params -> 'prescription') ? 'rep_limits',
        (params -> 'prescription') ? 'by_goal'
INTO tolerancia, tiene_limites, tiene_by_goal
FROM calculation_configs WHERE is_active;

IF tolerancia IS DISTINCT FROM 5.0 THEN
        RAISE EXCEPTION 'volume_tolerance_pct deberia ser 5.0 y es %', tolerancia;
END IF;
    IF NOT tiene_limites OR tiene_by_goal THEN
        RAISE EXCEPTION 'prescription no quedo con rep_limits y sin by_goal';
END IF;
END $$;