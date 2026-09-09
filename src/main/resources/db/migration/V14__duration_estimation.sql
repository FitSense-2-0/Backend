-- =====================================================================
-- FitSense MVP 1.0 - V14: estimacion de duracion de sesion
--
-- POR QUE
-- expected_duration_minutes es el PESO de la adherencia ponderada, la metrica
-- primaria, y nadie comprobaba que fuera real. El motor de reglas copiaba
-- session_minutes del perfil; la IA declaraba su estimacion.
--
-- El resultado observado el 2026-09-07 fue el contrario del buscado: con un
-- ajuste de -30 % de volumen, la IA declaro 28 y 26 minutos —coherente con su
-- contenido— y la validacion 17 la rechazo por no llegar al piso de 32. El
-- motor de reglas declaro 45 para un contenido de ~27 y paso sin problema.
-- De los dos generadores, el que declara mal era el que pasaba.
--
-- Y como el fallo se dispara justo cuando el ajuste reduce el volumen, empuja
-- de forma sistematica a los participantes de baja adherencia hacia el motor
-- de reglas: en un diseno de dos brazos, contaminacion correlacionada con la
-- variable de resultado.
--
-- QUE CAMBIA
-- El piso absoluto del 70 % deja de aplicarse cuando hay reduccion de volumen
-- activa: el volumen es la intervencion que el estudio prueba, session_minutes
-- es una preferencia del usuario, y con 272 repeticiones en dos dias no se
-- llenan 45 minutos. Algo tiene que ceder, y debe ceder la preferencia.
--
-- En su lugar se compara la duracion DECLARADA contra la ESTIMADA a partir del
-- contenido real. Ver §19.3 V17 y §20.4, ambos modificados.
--
-- SIN CALIBRAR
-- seconds_per_rep, transition_seconds y warmup_minutes son estimaciones del
-- desarrollador, no valores de referencia. Como el resto de umbrales viven
-- aqui: recalibrar tras el piloto es insertar una fila, no recompilar.
-- =====================================================================

UPDATE calculation_configs SET is_active = FALSE WHERE version = 'MVP-1.1';

INSERT INTO calculation_configs (version, description, params, is_active)
SELECT
    'MVP-1.2',
    'Anade parametros de estimacion de duracion de sesion. Sin calibrar.',
    jsonb_set(params, '{prescription}',
              (params -> 'prescription') || jsonb_build_object(
                  -- Tiempo de trabajo por repeticion. Cubre fase concentrica,
                  -- excentrica y la pausa breve entre repeticiones.
                      'seconds_per_rep',       3,
                  -- Cambio de estacion o de material entre un ejercicio y el siguiente.
                      'transition_seconds',    60,
                  -- Calentamiento y preparacion, fijo por sesion.
                      'warmup_minutes',        5,
                  -- Se usa cuando el ejercicio no declara descanso.
                      'default_rest_seconds',  60,
                  -- Desvio maximo admitido entre lo declarado y lo estimado (V17).
                      'duration_tolerance_pct', 20
                                            )
    ),
    TRUE
FROM calculation_configs WHERE version = 'MVP-1.1';


DO $$
DECLARE
activas INTEGER;
    tiene   BOOLEAN;
BEGIN
SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
SELECT (params -> 'prescription') ? 'seconds_per_rep' INTO tiene
FROM calculation_configs WHERE is_active;

IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
    IF NOT tiene THEN
        RAISE EXCEPTION 'La configuracion activa no tiene los parametros de duracion';
END IF;

    RAISE NOTICE 'V14 aplicada: MVP-1.2 activa con estimacion de duracion';
END $$;