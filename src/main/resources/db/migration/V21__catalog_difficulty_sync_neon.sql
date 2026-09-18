-- =====================================================================
-- FitSense MVP 1.0 - V21: sincroniza con el repositorio la dificultad que
-- solo existia en Neon
--
-- POR QUE
-- Al comparar el catalogo de Neon con el que producen las migraciones V1-V20
-- (17 de septiembre de 2026), las 20 migraciones tenian el mismo checksum,
-- pero 33 ejercicios tenian otra dificultad en Neon. No hay registro de
-- cuando ni como se cambiaron: no salen de ninguna migracion y no siguen una
-- regla de palabras clave que se pueda reconstruir (por ejemplo, "scapular
-- pull-up", "pull up (neutral grip)", "muscle up" y los fondos en banco
-- siguieron en 1).
--
-- Mientras eso viviera solo en Neon, un entorno nuevo —incluida la base del
-- estudio— tendria un catalogo distinto al de las pruebas, que es lo que V11
-- quiso evitar.
--
-- QUE HACE
-- Pone difficulty_level = 3 en esos 33 ejercicios (dominadas y chin-ups,
-- fondos en paralelas, barra o anillas, levers, planches, parada de manos y
-- pistol). Se identifican por source_code (codigo del dataset) y se comprueba
-- name_en: si algo no coincide, la migracion aborta sin tocar nada.
--
-- EN NEON no cambia ninguna fila: ya tienen 3. En un entorno nuevo deja el
-- catalogo identico al de Neon (huella a63d7ebe407d209b8adcce19652880d0 con la
-- consulta de comparar-catalogo-neon.sql).
--
-- ADVERTENCIA METODOLOGICA
-- Igual que V10, esto NO es la clasificacion de dificultad que exige la
-- seccion 8 (dos evaluadores con acuerdo reportable). Solo hace reproducible
-- el estado con el que se hicieron las pruebas. La revision manual del
-- catalogo sigue pendiente y debe entrar como migracion propia.
--
-- NO editar ni reformatear este archivo despues de aplicarlo (checksum de
-- Flyway, incidente de V18 y V19).
-- =====================================================================

-- Tabla temporal de trabajo. Sin ON COMMIT DROP, porque fuera de una
-- transaccion (psql) desapareceria antes del INSERT; se borra al final.
CREATE TEMP TABLE v21_dificultad (
    source_code VARCHAR(20)  PRIMARY KEY,
    name_en     VARCHAR(255) NOT NULL
);

INSERT INTO v21_dificultad (source_code, name_en) VALUES
                                                      ('3297', 'back lever'),
                                                      ('0140', 'biceps pull-up'),
                                                      ('0251', 'chest dip'),
                                                      ('1430', 'chest dip (on dip-pull-up cage)'),
                                                      ('2462', 'chest dip on straight bar'),
                                                      ('1326', 'chin-up'),
                                                      ('1327', 'close grip chin-up'),
                                                      ('3327', 'full planche push-up'),
                                                      ('3301', 'frog planche'),
                                                      ('3296', 'front lever'),
                                                      ('3299', 'full planche'),
                                                      ('3295', 'front lever reps'),
                                                      ('3302', 'handstand'),
                                                      ('0471', 'handstand push-up'),
                                                      ('3289', 'impossible dips'),
                                                      ('3288', 'korean dips'),
                                                      ('3300', 'lean planche'),
                                                      ('3418', 'l-pull-up'),
                                                      ('0627', 'mixed grip chin-up'),
                                                      ('1401', 'muscle-up (on vertical bar)'),
                                                      ('0638', 'one arm chin-up'),
                                                      ('0652', 'pull-up'),
                                                      ('0670', 'rear pull-up'),
                                                      ('0674', 'reverse grip pull-up'),
                                                      ('0678', 'rocky pull-up pulldown'),
                                                      ('0677', 'ring dips'),
                                                      ('1763', 'shoulder grip pull-up'),
                                                      ('1759', 'single leg squat (pistol) male'),
                                                      ('3298', 'straddle planche'),
                                                      ('0814', 'triceps dip'),
                                                      ('2363', 'wide-grip chest dip on high parallel bars'),
                                                      ('1429', 'wide grip pull-up'),
                                                      ('1367', 'wide grip rear pull-up');

DO $$
DECLARE
encontrados   INTEGER;
    distintos     TEXT;
BEGIN
SELECT COUNT(*) INTO encontrados
FROM exercises e
         JOIN v21_dificultad v ON v.source_code = e.source_code AND v.name_en = e.name_en;

IF encontrados <> 33 THEN
SELECT string_agg(v.source_code || ' (' || v.name_en || ')', ', ') INTO distintos
FROM v21_dificultad v
         LEFT JOIN exercises e ON e.source_code = v.source_code AND e.name_en = v.name_en
WHERE e.exercise_id IS NULL;
RAISE EXCEPTION 'V21: se esperaban 33 ejercicios y coinciden %. No encontrados: %',
            encontrados, distintos;
END IF;
END $$;

UPDATE exercises e
SET difficulty_level = 3
    FROM v21_dificultad v
WHERE v.source_code = e.source_code
  AND v.name_en = e.name_en
  AND e.difficulty_level <> 3;

DO $$
DECLARE
pendientes INTEGER;
BEGIN
SELECT COUNT(*) INTO pendientes
FROM exercises e
         JOIN v21_dificultad v ON v.source_code = e.source_code
WHERE e.difficulty_level <> 3;

IF pendientes <> 0 THEN
        RAISE EXCEPTION 'V21: quedaron % ejercicios sin dificultad 3', pendientes;
END IF;

    RAISE NOTICE 'V21 aplicada: 33 ejercicios con dificultad 3 (igual que Neon)';
END $$;

DROP TABLE v21_dificultad;