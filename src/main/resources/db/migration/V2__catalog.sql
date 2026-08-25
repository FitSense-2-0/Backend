-- =====================================================================
-- FitSense MVP 1.0 - V2: Modulo B (Catalogo)
-- Diseno: secciones 6, 7 y 8
-- Catalogo de solo lectura: se importa una vez y no se edita nunca.
-- =====================================================================

CREATE TABLE body_parts (
    body_part_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(40) NOT NULL,
    name_es       VARCHAR(80) NOT NULL,
    display_order SMALLINT    NOT NULL,
    CONSTRAINT uq_body_parts_code UNIQUE (code)
);

CREATE TABLE equipment_types (
    equipment_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(40) NOT NULL,
    name_es       VARCHAR(80) NOT NULL,
    requires_gym  BOOLEAN     NOT NULL DEFAULT FALSE,
    display_order SMALLINT    NOT NULL,
    CONSTRAINT uq_equipment_types_code UNIQUE (code)
);

COMMENT ON COLUMN equipment_types.requires_gym IS
    'true si no es razonable tenerlo en casa. Con training_location = HOME se excluye del conjunto elegible.';


CREATE TABLE exercises (
    exercise_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_code          VARCHAR(20)   NOT NULL,
    name_en              VARCHAR(255)  NOT NULL,
    name_es              VARCHAR(255),
    body_part_id         SMALLINT      NOT NULL,
    equipment_id         SMALLINT      NOT NULL,
    target_muscle        VARCHAR(60)   NOT NULL,
    synergist_muscle     VARCHAR(60),
    secondary_muscles    VARCHAR(60)[],
    instructions_es      TEXT,
    instruction_steps_es JSONB,
    instructions_en      TEXT,
    image_path           VARCHAR(255),
    gif_path             VARCHAR(255),
    media_attribution    VARCHAR(200),
    difficulty_level     SMALLINT      NOT NULL DEFAULT 2,
    default_prescription VARCHAR(25)   NOT NULL DEFAULT 'SETS_REPS',
    is_active            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_exercises_source_code UNIQUE (source_code),
    CONSTRAINT fk_exercises_body_part
        FOREIGN KEY (body_part_id) REFERENCES body_parts (body_part_id),
    CONSTRAINT fk_exercises_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment_types (equipment_id),
    CONSTRAINT ck_exercises_difficulty   CHECK (difficulty_level BETWEEN 1 AND 3),
    CONSTRAINT ck_exercises_prescription CHECK (default_prescription IN ('SETS_REPS','DURATION')),
    -- Un ejercicio activo es seleccionable por la IA: debe estar traducido
    CONSTRAINT ck_exercises_active_translated
        CHECK (NOT is_active OR name_es IS NOT NULL)
);

COMMENT ON TABLE exercises IS
    'Una sola tabla. El catalogo no cambia, asi que normalizar solo agregaba JOINs.';
COMMENT ON COLUMN exercises.is_active IS
    'Solo el subconjunto activo (~150-200 de 1324) es elegible por la IA.';
COMMENT ON COLUMN exercises.media_attribution IS
    'Credito obligatorio de las imagenes y GIF: (c) Gym visual.';

-- Indice de seleccion: es el filtro exacto del generador y del prompt
CREATE INDEX ix_exercises_selection
    ON exercises (body_part_id, equipment_id, difficulty_level)
    WHERE is_active;
