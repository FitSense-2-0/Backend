-- =====================================================================
-- FitSense MVP 1.0 - V3: Datos del catalogo
-- Tablas de traduccion (10 + 15 filas). Los 1,324 ejercicios se cargan
-- en V3_1 (ver nota al final del archivo).
-- =====================================================================

INSERT INTO body_parts (code, name_es, display_order) VALUES
    ('upper arms',  'Brazos',       1),
    ('upper legs',  'Piernas',      2),
    ('back',        'Espalda',      3),
    ('waist',       'Core',         4),
    ('chest',       'Pecho',        5),
    ('shoulders',   'Hombros',      6),
    ('lower legs',  'Pantorrillas', 7),
    ('lower arms',  'Antebrazos',   8),
    ('cardio',      'Cardio',       9),
    ('neck',        'Cuello',      10);

INSERT INTO equipment_types (code, name_es, requires_gym, display_order) VALUES
    ('body weight',     'Peso corporal',       FALSE,  1),
    ('dumbbell',        'Mancuerna',           FALSE,  2),
    ('band',            'Banda elastica',      FALSE,  3),
    ('kettlebell',      'Pesa rusa',           FALSE,  4),
    ('stability ball',  'Balon de estabilidad',FALSE,  5),
    ('medicine ball',   'Balon medicinal',     FALSE,  6),
    ('rope',            'Cuerda',              FALSE,  7),
    ('barbell',         'Barra',               TRUE,   8),
    ('ez barbell',      'Barra EZ',            TRUE,   9),
    ('olympic barbell', 'Barra olimpica',      TRUE,  10),
    ('cable',           'Polea',               TRUE,  11),
    ('leverage machine','Maquina',             TRUE,  12),
    ('smith machine',   'Maquina Smith',       TRUE,  13),
    ('sled machine',    'Maquina de trineo',   TRUE,  14),
    ('weighted',        'Lastre',              TRUE,  15),
    ('assisted',        'Asistido',            TRUE,  16),
    ('other',           'Otro',                FALSE, 17);

-- ---------------------------------------------------------------------
-- NOTA SOBRE LOS 1,324 EJERCICIOS
-- ---------------------------------------------------------------------
-- El dataset no se pega a mano en esta migracion. El flujo es:
--
--   1. Ejecutar tools/generate_exercises_migration.py sobre el JSON del
--      dataset. Produce V3_1__catalog_exercises.sql con un unico INSERT
--      multi-fila y todo is_active = FALSE.
--   2. Curar el subconjunto activo (150-200 ejercicios): asignar name_es,
--      difficulty_level y default_prescription. Sale como
--      V3_2__catalog_active_subset.sql, que son UPDATEs.
--   3. Registrar en el comentario de cabecera de cada archivo el checksum
--      sha256 del dataset de origen (reemplaza a dataset_imports).
--
-- La curacion la hacen los dos autores en paralelo sobre un subconjunto
-- comun y se reporta la concordancia en la metodologia (seccion 8).
-- ---------------------------------------------------------------------
