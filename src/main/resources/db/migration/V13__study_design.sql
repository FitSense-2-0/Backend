-- =====================================================================
-- FitSense MVP 1.0 - V13: volumen planificado y ejecutado
--
-- POR QUE
-- weighted_adherence_pct mide cumplimiento CONTRA EL PLAN, y el plan cambia
-- cada semana por efecto del ajuste. Eso hace que el porcentaje suba cuando
-- el plan baja, aunque el participante entrene lo mismo: el denominador es
-- endogeno.
--
-- Estas dos columnas dan la lectura con denominador fijo. planned_week_volume
-- es lo que se pidio; executed_volume es lo que se hizo. Ninguna depende del
-- ajuste, asi que la pareja responde dos preguntas distintas:
--
--   weighted_adherence_pct  -> cumplio lo que se le pidio?
--   executed_volume         -> entreno mas que la semana pasada?
--
-- Sin la segunda, "la adherencia mejoro" es indistinguible de "se bajo la vara".
--
-- UNIDAD
-- Repeticiones equivalentes, la misma de 18.1: repeticiones reales para los
-- ejercicios de series y repeticiones, y segundos reales divididos entre
-- duration_to_reps_divisor para los de duracion.
--
-- SIN TOPE, a diferencia de la adherencia. El tope del 100 % existe para que
-- sobrecumplir no compense una sesion saltada. Aqui el objetivo es el
-- contrario: medir el trabajo real, y hacer de mas ES trabajo real.
-- =====================================================================

ALTER TABLE weekly_user_metrics
    ADD COLUMN planned_week_volume INTEGER,
    ADD COLUMN executed_volume     INTEGER NOT NULL DEFAULT 0;

ALTER TABLE weekly_user_metrics
    ADD CONSTRAINT ck_wum_executed_volume CHECK (executed_volume >= 0);
ALTER TABLE weekly_user_metrics
    ADD CONSTRAINT ck_wum_planned_volume
        CHECK (planned_week_volume IS NULL OR planned_week_volume >= 0);

-- Sin plan no hay volumen planificado, por la misma razon que la adherencia
-- va NULL: no existe denominador. Un cero contaminaria los promedios.
ALTER TABLE weekly_user_metrics
    ADD CONSTRAINT ck_wum_planned_volume_null_when_no_plan
        CHECK (has_active_plan OR planned_week_volume IS NULL);

COMMENT ON COLUMN weekly_user_metrics.planned_week_volume IS
    'Volumen prescrito de la semana en repeticiones equivalentes (18.1). NULL si no hubo plan.';
COMMENT ON COLUMN weekly_user_metrics.executed_volume IS
    'Volumen realmente ejecutado, misma unidad, SIN TOPE. Lectura con denominador fijo: no se mueve cuando el ajuste cambia el plan.';