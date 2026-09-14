-- =====================================================================
-- FitSense MVP 1.0 - V17: componentes crudos del volumen
--
-- POR QUE
-- El volumen equivalente mezcla dos magnitudes con un factor elegido:
-- duration_to_reps_divisor = 30, o sea 30 segundos = 1 repeticion. Ese numero
-- no sale de ninguna referencia, y ya esta declarado como tal.
--
-- No es un problema donde el sistema DECIDE (validar un plan, reducir volumen):
-- ahi el factor es identico en los dos brazos del estudio, asi que no puede
-- explicar ninguna diferencia entre ellos. Se cancela, igual que la regla de
-- reduccion.
--
-- Si lo es donde el sistema MIDE. planned_week_volume y executed_volume son la
-- defensa contra el denominador endogeno: la lectura que permite decir "la
-- adherencia subio y el trabajo real tambien". Si ese numero depende de una
-- constante arbitraria, la defensa hereda la arbitrariedad.
--
-- QUE SE HACE, Y QUE NO
-- NO se quita el equivalente. Sin una magnitud unica no se pueden comparar
-- semanas de composicion distinta: una lleva tres planchas y la siguiente
-- ninguna, y entonces "480 reps + 900 s" contra "500 reps + 0 s" no responde
-- si entreno mas o menos.
--
-- Se guardan ademas los INGREDIENTES antes de mezclarlos. Con ellos el analisis
-- de sensibilidad deja de ser un argumento y pasa a ser una consulta:
--
--   SELECT executed_reps + ROUND(executed_seconds / 10.0) AS con_factor_10,
--          executed_volume                                AS con_factor_30,
--          executed_reps + ROUND(executed_seconds / 60.0) AS con_factor_60
--   FROM weekly_user_metrics WHERE user_id = ?;
--
-- Y la proporcion de volumen que viene de ejercicios de duracion se vuelve
-- reportable: ese porcentaje es la COTA de cuanto puede importar el factor.
-- En los datos de prueba fue 3 %, y todo el rango del divisor movio el total
-- menos de un 7 %.
-- =====================================================================

ALTER TABLE weekly_user_metrics
    ADD COLUMN planned_reps      INTEGER,
    ADD COLUMN planned_seconds   INTEGER,
    ADD COLUMN executed_reps     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN executed_seconds  INTEGER NOT NULL DEFAULT 0;

ALTER TABLE weekly_user_metrics
    ADD CONSTRAINT ck_wum_components_non_negative
        CHECK (executed_reps >= 0 AND executed_seconds >= 0
            AND (planned_reps    IS NULL OR planned_reps    >= 0)
            AND (planned_seconds IS NULL OR planned_seconds >= 0));

-- Sin plan no hay prescripcion, igual que planned_week_volume.
ALTER TABLE weekly_user_metrics
    ADD CONSTRAINT ck_wum_components_null_when_no_plan
        CHECK (has_active_plan OR (planned_reps IS NULL AND planned_seconds IS NULL));

COMMENT ON COLUMN weekly_user_metrics.planned_reps IS
    'Repeticiones prescritas, sin convertir. NULL si no hubo plan.';
COMMENT ON COLUMN weekly_user_metrics.planned_seconds IS
    'Segundos prescritos en ejercicios de duracion, sin convertir. NULL si no hubo plan.';
COMMENT ON COLUMN weekly_user_metrics.executed_reps IS
    'Repeticiones realmente ejecutadas, sin convertir ni topar.';
COMMENT ON COLUMN weekly_user_metrics.executed_seconds IS
    'Segundos realmente ejecutados, sin convertir ni topar.';