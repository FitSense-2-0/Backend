-- =====================================================================
-- FitSense MVP 1.0 - V12: rangos de prescripcion por objetivo
--
-- Anade el bloque "prescription" a calculation_configs.params.
--
-- POR QUE
-- El objetivo del participante llegaba al prompt pero nada obligaba a
-- respetarlo. En la prueba, un perfil LOSE_WEIGHT recibio 3x8: ocho
-- repeticiones es rango de fuerza, no de perdida de peso. Y una sesion
-- declarada de 45 minutos salio con 25.
--
-- La tabla 20.4 fija una prescripcion por objetivo, pero solo la sigue el motor
-- de reglas. La IA la ve como sugerencia. Con estos rangos, las validaciones 16
-- y 17 pueden rechazar lo que se salga.
--
-- SON RANGOS, NO VALORES EXACTOS
-- La IA debe poder variar entre ejercicios —un accesorio admite mas
-- repeticiones que un basico— pero no irse a otro objetivo. Por eso min y max
-- en vez del valor de 20.4, que queda como referencia del motor de reglas.
--
-- SIN CALIBRAR
-- Los rangos son criterio estandar de entrenamiento, no valores validados para
-- esta poblacion. Como el resto de umbrales, se ajustan tras el piloto
-- insertando una fila nueva en calculation_configs: no hay que recompilar, y
-- cada metrica queda ligada a la version con la que se produjo.
-- =====================================================================

-- Se crea una version nueva en vez de editar MVP-1.0: las metricas ya
-- calculadas deben seguir apuntando a los umbrales con los que se produjeron.
INSERT INTO calculation_configs (version, description, params, is_active)
SELECT
    'MVP-1.1',
    'Anade rangos de prescripcion por objetivo y piso de duracion de sesion. Sin calibrar.',
    params || jsonb_build_object(
            'prescription', jsonb_build_object(
                -- Piso de duracion: ninguna sesion puede bajar de este porcentaje
                -- de session_minutes. El techo (+15 %) ya estaba en la validacion 4.
                    'session_minutes_floor_pct', 70,

                    'by_goal', jsonb_build_object(
                            'LOSE_WEIGHT',       jsonb_build_object('min_reps', 12, 'max_reps', 20),
                            'GAIN_MUSCLE',       jsonb_build_object('min_reps',  8, 'max_reps', 15),
                            'INCREASE_STRENGTH', jsonb_build_object('min_reps',  4, 'max_reps', 10),
                            'GENERAL_FITNESS',   jsonb_build_object('min_reps',  8, 'max_reps', 15)
                               )
                            )
              ),
    TRUE
FROM calculation_configs WHERE version = 'MVP-1.0';

-- ux_config_active solo admite una activa: se desactiva la anterior DESPUES de
-- insertar la nueva para no dejar el sistema sin configuracion ni un instante.
UPDATE calculation_configs SET is_active = FALSE WHERE version = 'MVP-1.0';


DO $$
DECLARE
activas INTEGER;
    tiene   BOOLEAN;
BEGIN
SELECT COUNT(*) INTO activas FROM calculation_configs WHERE is_active;
SELECT params ? 'prescription' INTO tiene
FROM calculation_configs WHERE is_active;

IF activas <> 1 THEN
        RAISE EXCEPTION 'Debe haber exactamente una configuracion activa y hay %', activas;
END IF;
    IF NOT tiene THEN
        RAISE EXCEPTION 'La configuracion activa no tiene el bloque prescription';
END IF;

    RAISE NOTICE 'V12 aplicada: MVP-1.1 activa con rangos de prescripcion';
END $$;
