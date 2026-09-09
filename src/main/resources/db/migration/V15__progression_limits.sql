-- =====================================================================
-- FitSense MVP 1.0 - V15: limites de progresion
--
-- POR QUE
-- 18.2 sube el volumen 5 % cada semana con adherencia >= 80 %, y ese aumento
-- no tiene techo: bajar nunca pasa del -40 % acumulado, subir no tiene tope.
-- En doce semanas son +71 %.
--
-- El problema no es el 5 %. Son dos cosas alrededor:
--
-- 1. Se progresa por ADHERENCIA, que mide que la persona hizo lo pedido, no
--    que le resultara llevadero. Dos participantes con 85 % de adherencia, uno
--    con RPE 5 y otro con RPE 9, recibian el mismo aumento.
--
-- 2. El sistema escala hasta que la persona ya no puede cumplir, entonces baja
--    y vuelve a subir. Esa oscilacion es un artefacto de la regla y en los
--    resultados PARECE adaptacion. Peor: convierte el abandono en producto de
--    la regla de progresion, y el abandono es variable de resultado.
--
-- QUE CAMBIA
-- progression_required_weeks = 2
--   El ACSM condiciona el incremento a sostener el desempeno en DOS sesiones
--   consecutivas, no al calendario (Position Stand 2009, regla del "2 por 2":
--   incremento de 2-10 % cuando se superan 1-2 repeticiones del objetivo en dos
--   sesiones seguidas). Aqui la unidad es la semana, no la sesion, por la
--   cadencia semanal del sistema.
--
-- max_cumulative_volume_increase_pct = 25
--   SIN REFERENCIA. Decision de diseno declarada: limite conservador para un
--   piloto de doce semanas con principiantes, simetrico al piso del -40 % que
--   ya existe. Circula una regla practica de no superar el 10 % semanal, pero
--   las propias fuentes la reconocen poco practica en los extremos.
--
-- progression_rpe_ceiling = NULL  (DESACTIVADO A PROPOSITO)
--   Es la condicion correcta: no progresar a quien reporta esfuerzo alto. Pero
--   session_rpe es hoy un entero 1-10 opcional y sin anclajes verbales, y
--   Foster et al. (2001) exigen familiarizacion con la escala antes de recoger
--   medidas fiables. Decidir con ese dato es decidir con ruido. El parametro
--   queda escrito para activarlo cuando la escala este instrumentada; mientras
--   tanto el RPE se recoge y se reporta como variable descriptiva.
--
-- NOTA: la progresion del 5 % se mantiene. Cae dentro del rango 2-10 % del
-- ACSM. La limitacion a declarar es otra: esas referencias hablan de progresar
-- la CARGA, y FitSense progresa el VOLUMEN porque nunca prescribe kilos.
-- =====================================================================

UPDATE calculation_configs SET is_active = FALSE WHERE version = 'MVP-1.2';

INSERT INTO calculation_configs (version, description, params, is_active)
SELECT
    'MVP-1.3',
    'Anade limites de progresion: criterio de dos semanas y techo acumulado.',
    jsonb_set(params, '{adjustment}',
              (params -> 'adjustment') || jsonb_build_object(
                      'progression_required_weeks',         2,
                      'max_cumulative_volume_increase_pct', 25,
                      'progression_rpe_ceiling',            'null'::jsonb
                                          )
    ),
    TRUE
FROM calculation_configs WHERE version = 'MVP-1.2';


DO $$
DECLARE
activas INTEGER;
    techo   INTEGER;
BEGIN
SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
SELECT (params -> 'adjustment' ->> 'max_cumulative_volume_increase_pct')::int
INTO techo FROM calculation_configs WHERE is_active;

IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
    IF techo IS NULL THEN
        RAISE EXCEPTION 'La configuracion activa no tiene el techo de progresion';
END IF;

    RAISE NOTICE 'V15 aplicada: MVP-1.3 activa, techo de progresion +% %%', techo;
END $$;