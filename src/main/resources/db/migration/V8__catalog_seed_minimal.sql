-- =====================================================================
-- FitSense MVP 1.0 - V8: Semilla minima del catalogo
--
-- POR QUE EXISTE ESTE ARCHIVO
-- V3 carga las tablas de traduccion pero deja exercises VACIA, con una nota
-- que remite a un "V3_1" que nunca se escribio. Con la tabla vacia el conjunto
-- elegible es vacio y NINGUN plan puede generarse: el backend arranca, la API
-- responde, y POST /api/v1/plans/generate falla siempre.
--
-- Esta migracion carga 52 ejercicios curados, suficientes para cubrir los siete
-- focos (ck_pw_focus) tanto en casa como en gimnasio, y hace el ciclo completo
-- ejecutable de punta a punta.
--
-- NO reemplaza la importacion de los 1,324 ejercicios. Cuando ese dataset este
-- listo, cargalo en una migracion posterior; estos source_code usan el prefijo
-- MVP- justamente para no colisionar con los codigos de la fuente original.
--
-- Sin media: image_path y gif_path quedan NULL. La app debe tolerarlo.
-- =====================================================================

INSERT INTO exercises (source_code, name_en, name_es, body_part_id, equipment_id,
                       target_muscle, difficulty_level, default_prescription, is_active)
SELECT v.source_code, v.name_en, v.name_es, bp.body_part_id, eq.equipment_id,
       v.target_muscle, v.difficulty::SMALLINT, v.prescription, TRUE
FROM (VALUES
          -- ---------------------------------------------------------- chest (pecho)
          ('MVP-0001', 'Push-up',                  'Flexiones de pecho',            'chest',      'body weight', 'Pectoralis Major', 1, 'SETS_REPS'),
          ('MVP-0002', 'Knee push-up',             'Flexiones con rodillas apoyadas','chest',     'body weight', 'Pectoralis Major', 1, 'SETS_REPS'),
          ('MVP-0003', 'Incline push-up',          'Flexiones inclinadas',          'chest',      'body weight', 'Pectoralis Major', 1, 'SETS_REPS'),
          ('MVP-0004', 'Diamond push-up',          'Flexiones diamante',            'chest',      'body weight', 'Triceps Brachii',  3, 'SETS_REPS'),
          ('MVP-0005', 'Dumbbell bench press',     'Press de banca con mancuernas', 'chest',      'dumbbell',    'Pectoralis Major', 2, 'SETS_REPS'),
          ('MVP-0006', 'Dumbbell fly',             'Aperturas con mancuernas',      'chest',      'dumbbell',    'Pectoralis Major', 2, 'SETS_REPS'),
          ('MVP-0007', 'Barbell bench press',      'Press de banca con barra',      'chest',      'barbell',     'Pectoralis Major', 3, 'SETS_REPS'),
          ('MVP-0008', 'Cable crossover',          'Cruces en polea',               'chest',      'cable',       'Pectoralis Major', 2, 'SETS_REPS'),

          -- ---------------------------------------------------------- back (espalda)
          ('MVP-0009', 'Superman',                 'Superman',                      'back',       'body weight', 'Erector Spinae',   1, 'SETS_REPS'),
          ('MVP-0010', 'Bird dog',                 'Bird dog',                      'back',       'body weight', 'Erector Spinae',   1, 'SETS_REPS'),
          ('MVP-0011', 'Band pull-apart',          'Aperturas con banda',           'back',       'band',        'Trapezius',        1, 'SETS_REPS'),
          ('MVP-0012', 'Band bent-over row',       'Remo inclinado con banda',      'back',       'band',        'Latissimus Dorsi', 2, 'SETS_REPS'),
          ('MVP-0013', 'Dumbbell bent-over row',   'Remo inclinado con mancuernas', 'back',       'dumbbell',    'Latissimus Dorsi', 2, 'SETS_REPS'),
          ('MVP-0014', 'Dumbbell single-arm row',  'Remo a una mano con mancuerna', 'back',       'dumbbell',    'Latissimus Dorsi', 2, 'SETS_REPS'),
          ('MVP-0015', 'Pull-up',                  'Dominadas',                     'back',       'body weight', 'Latissimus Dorsi', 3, 'SETS_REPS'),
          ('MVP-0016', 'Lat pulldown',             'Jalon al pecho en polea',       'back',       'cable',       'Latissimus Dorsi', 2, 'SETS_REPS'),
          ('MVP-0017', 'Seated cable row',         'Remo sentado en polea',         'back',       'cable',       'Latissimus Dorsi', 2, 'SETS_REPS'),

          -- ------------------------------------------------------ shoulders (hombros)
          ('MVP-0018', 'Pike push-up',             'Flexiones pike',                'shoulders',  'body weight', 'Deltoid Anterior', 3, 'SETS_REPS'),
          ('MVP-0019', 'Arm circles',              'Circulos de brazos',            'shoulders',  'body weight', 'Deltoid Lateral',  1, 'DURATION'),
          ('MVP-0020', 'Dumbbell shoulder press',  'Press militar con mancuernas',  'shoulders',  'dumbbell',    'Deltoid Anterior', 2, 'SETS_REPS'),
          ('MVP-0021', 'Dumbbell lateral raise',   'Elevaciones laterales',         'shoulders',  'dumbbell',    'Deltoid Lateral',  1, 'SETS_REPS'),
          ('MVP-0022', 'Dumbbell front raise',     'Elevaciones frontales',         'shoulders',  'dumbbell',    'Deltoid Anterior', 1, 'SETS_REPS'),
          ('MVP-0023', 'Band face pull',           'Face pull con banda',           'shoulders',  'band',        'Deltoid Posterior',2, 'SETS_REPS'),
          ('MVP-0024', 'Barbell overhead press',   'Press militar con barra',       'shoulders',  'barbell',     'Deltoid Anterior', 3, 'SETS_REPS'),

          -- ---------------------------------------------------- upper arms (brazos)
          ('MVP-0025', 'Bench dip',                'Fondos en banco',               'upper arms', 'body weight', 'Triceps Brachii',  2, 'SETS_REPS'),
          ('MVP-0026', 'Dumbbell biceps curl',     'Curl de biceps con mancuernas', 'upper arms', 'dumbbell',    'Biceps Brachii',   1, 'SETS_REPS'),
          ('MVP-0027', 'Dumbbell hammer curl',     'Curl martillo',                 'upper arms', 'dumbbell',    'Brachialis',       1, 'SETS_REPS'),
          ('MVP-0028', 'Dumbbell triceps ext.',    'Extension de triceps',          'upper arms', 'dumbbell',    'Triceps Brachii',  2, 'SETS_REPS'),
          ('MVP-0029', 'Band biceps curl',         'Curl de biceps con banda',      'upper arms', 'band',        'Biceps Brachii',   1, 'SETS_REPS'),
          ('MVP-0030', 'Cable triceps pushdown',   'Extension de triceps en polea', 'upper arms', 'cable',       'Triceps Brachii',  2, 'SETS_REPS'),
          ('MVP-0031', 'EZ-bar curl',              'Curl con barra EZ',             'upper arms', 'ez barbell',  'Biceps Brachii',   2, 'SETS_REPS'),

          -- ---------------------------------------------------- upper legs (piernas)
          ('MVP-0032', 'Bodyweight squat',         'Sentadillas sin peso',          'upper legs', 'body weight', 'Quadriceps',       1, 'SETS_REPS'),
          ('MVP-0033', 'Forward lunge',            'Zancadas',                      'upper legs', 'body weight', 'Quadriceps',       2, 'SETS_REPS'),
          ('MVP-0034', 'Glute bridge',             'Puente de gluteos',             'upper legs', 'body weight', 'Gluteus Maximus',  1, 'SETS_REPS'),
          ('MVP-0035', 'Wall sit',                 'Sentadilla isometrica en pared','upper legs', 'body weight', 'Quadriceps',       2, 'DURATION'),
          ('MVP-0036', 'Bulgarian split squat',    'Sentadilla bulgara',            'upper legs', 'body weight', 'Quadriceps',       3, 'SETS_REPS'),
          ('MVP-0037', 'Dumbbell goblet squat',    'Sentadilla goblet',             'upper legs', 'dumbbell',    'Quadriceps',       2, 'SETS_REPS'),
          ('MVP-0038', 'Dumbbell Romanian dl',     'Peso muerto rumano',            'upper legs', 'dumbbell',    'Hamstrings',       2, 'SETS_REPS'),
          ('MVP-0039', 'Dumbbell step-up',         'Subida al cajon con mancuernas','upper legs', 'dumbbell',    'Gluteus Maximus',  2, 'SETS_REPS'),
          ('MVP-0040', 'Barbell back squat',       'Sentadilla con barra',          'upper legs', 'barbell',     'Quadriceps',       3, 'SETS_REPS'),
          ('MVP-0041', 'Leg press',                'Prensa de piernas',             'upper legs', 'leverage machine', 'Quadriceps',  2, 'SETS_REPS'),

          -- ------------------------------------------------ lower legs (pantorrillas)
          ('MVP-0042', 'Standing calf raise',      'Elevacion de talones de pie',   'lower legs', 'body weight', 'Gastrocnemius',    1, 'SETS_REPS'),
          ('MVP-0043', 'Single-leg calf raise',    'Elevacion de talon a una pierna','lower legs','body weight', 'Gastrocnemius',    2, 'SETS_REPS'),
          ('MVP-0044', 'Dumbbell calf raise',      'Elevacion de talones con peso', 'lower legs', 'dumbbell',    'Gastrocnemius',    2, 'SETS_REPS'),

          -- --------------------------------------------------------- waist (core)
          ('MVP-0045', 'Plank',                    'Plancha',                       'waist',      'body weight', 'Rectus Abdominis', 1, 'DURATION'),
          ('MVP-0046', 'Side plank',               'Plancha lateral',               'waist',      'body weight', 'Obliques',         2, 'DURATION'),
          ('MVP-0047', 'Crunch',                   'Abdominales cortos',            'waist',      'body weight', 'Rectus Abdominis', 1, 'SETS_REPS'),
          ('MVP-0048', 'Dead bug',                 'Dead bug',                      'waist',      'body weight', 'Rectus Abdominis', 1, 'SETS_REPS'),
          ('MVP-0049', 'Mountain climber',         'Escaladores',                   'waist',      'body weight', 'Rectus Abdominis', 2, 'DURATION'),
          ('MVP-0050', 'Russian twist',            'Giros rusos',                   'waist',      'body weight', 'Obliques',         2, 'SETS_REPS'),
          ('MVP-0051', 'Hanging knee raise',       'Elevacion de rodillas colgado', 'waist',      'body weight', 'Rectus Abdominis', 3, 'SETS_REPS'),
          ('MVP-0052', 'Stability ball rollout',   'Rollout con balon',             'waist',      'stability ball', 'Rectus Abdominis', 3, 'SETS_REPS')
     ) AS v(source_code, name_en, name_es, body_part_code, equipment_code, target_muscle, difficulty, prescription)
         JOIN body_parts      bp ON bp.code = v.body_part_code
         JOIN equipment_types eq ON eq.code = v.equipment_code;


-- Comprobacion dura: si algun code de la lista no existiera en las tablas de
-- traduccion, el JOIN lo habria descartado en silencio y el catalogo quedaria
-- incompleto sin que nadie se entere hasta que un plan salga corto.
DO $$
DECLARE
loaded INTEGER;
BEGIN
SELECT COUNT(*) INTO loaded FROM exercises WHERE source_code LIKE 'MVP-%';
IF loaded <> 52 THEN
        RAISE EXCEPTION 'Semilla incompleta: se esperaban 52 ejercicios y se cargaron %', loaded;
END IF;
END $$;
