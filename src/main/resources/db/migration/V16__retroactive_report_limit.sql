-- =====================================================================
-- FitSense MVP 1.0 - V16: limite temporal del reporte retroactivo
--
-- POR QUE
-- POST /workouts/{id}/report acepta cualquier performedAt pasado. Alguien
-- puede registrar en octubre un entrenamiento de agosto.
--
-- El reporte NO recalcula metricas, asi que no las reescribe de inmediato.
-- El problema es la incoherencia que deja: planned_workouts pasa a COMPLETED
-- mientras weekly_user_metrics sigue contando ese entrenamiento como saltado,
-- y user_interventions conserva la adherencia con la que decidio. Si esa
-- semana se recalcula despues, las tres tablas dejan de coincidir y el dato
-- exportado ya no es reconstruible.
--
-- El reporte retroactivo existe para cubrir el olvido de un dia o dos, no
-- para reabrir semanas cerradas.
-- =====================================================================

UPDATE calculation_configs SET is_active = FALSE WHERE version = 'MVP-1.3';

INSERT INTO calculation_configs (version, description, params, is_active)
SELECT
    'MVP-1.4',
    'Limita el reporte retroactivo a los ultimos dias.',
    jsonb_set(params, '{adherence}',
              (params -> 'adherence') || jsonb_build_object('retroactive_report_days', 7)
    ),
    TRUE
FROM calculation_configs WHERE version = 'MVP-1.3';


DO $$
DECLARE
activas INTEGER;
    dias    INTEGER;
BEGIN
SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
SELECT (params -> 'adherence' ->> 'retroactive_report_days')::int
INTO dias FROM calculation_configs WHERE is_active;

IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
    IF dias IS NULL THEN
        RAISE EXCEPTION 'La configuracion activa no tiene el limite retroactivo';
END IF;

    RAISE NOTICE 'V16 aplicada: MVP-1.4 activa, limite retroactivo de % dias', dias;
END $$;