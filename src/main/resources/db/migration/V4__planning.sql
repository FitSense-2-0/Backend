-- =====================================================================
-- FitSense MVP 1.0 - V4: Modulo C (Planificacion)
-- Diseno: secciones 9, 10 y 11
-- La planificacion es inmutable: aqui vive lo que el sistema INDICO.
-- =====================================================================

CREATE TABLE weekly_training_plans (
    plan_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    week_number         SMALLINT     NOT NULL,
    week_start_date     DATE         NOT NULL,
    week_end_date       DATE         NOT NULL,
    plan_version        SMALLINT     NOT NULL DEFAULT 1,
    parent_plan_id      BIGINT,
    planned_days_count  SMALLINT     NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    generation_source   VARCHAR(20)  NOT NULL,
    model_name          VARCHAR(100),
    input_snapshot      JSONB        NOT NULL,
    output_snapshot     JSONB,
    adjustment_applied  VARCHAR(40),
    adjustment_reason   VARCHAR(500),
    generation_attempts SMALLINT     NOT NULL DEFAULT 1,
    activated_at        TIMESTAMPTZ,
    replaced_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_plan_user   FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_plan_parent FOREIGN KEY (parent_plan_id) REFERENCES weekly_training_plans (plan_id),

    CONSTRAINT uq_plan_user_week_version UNIQUE (user_id, week_number, plan_version),
    CONSTRAINT ck_plan_status      CHECK (status IN ('ACTIVE','COMPLETED','REPLACED')),
    CONSTRAINT ck_plan_source      CHECK (generation_source IN ('AI','RULE_ENGINE')),
    CONSTRAINT ck_plan_dates       CHECK (week_end_date > week_start_date),
    CONSTRAINT ck_plan_days_count  CHECK (planned_days_count BETWEEN 1 AND 7),
    CONSTRAINT ck_plan_week_number CHECK (week_number >= 1),
    CONSTRAINT ck_plan_version     CHECK (plan_version >= 1),
    CONSTRAINT ck_plan_attempts    CHECK (generation_attempts >= 1)
);

COMMENT ON COLUMN weekly_training_plans.input_snapshot IS
    'Datos exactos enviados a la IA. Es la evidencia reproducible de la generacion.';
COMMENT ON COLUMN weekly_training_plans.parent_plan_id IS
    'Version anterior que esta reemplaza. Nunca se edita una semana: se versiona.';

-- Garantia a nivel de base: un solo plan activo por usuario y semana.
CREATE UNIQUE INDEX ux_plan_active
    ON weekly_training_plans (user_id, week_start_date)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_plan_user_week
    ON weekly_training_plans (user_id, week_start_date DESC);


CREATE TABLE planned_workouts (
    planned_workout_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id                   BIGINT      NOT NULL,
    scheduled_date            DATE        NOT NULL,
    focus_code                VARCHAR(30) NOT NULL,
    workout_name              VARCHAR(150) NOT NULL,
    expected_duration_minutes SMALLINT    NOT NULL,
    display_order             SMALLINT    NOT NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    skip_reason               VARCHAR(40),
    expires_at                TIMESTAMPTZ NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_planned_workout_plan
        FOREIGN KEY (plan_id) REFERENCES weekly_training_plans (plan_id) ON DELETE CASCADE,

    CONSTRAINT uq_planned_workout_order UNIQUE (plan_id, display_order),
    CONSTRAINT ck_pw_status CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','PARTIAL','SKIPPED','REPLACED')),
    CONSTRAINT ck_pw_focus  CHECK (focus_code IN ('UPPER_BODY','LOWER_BODY','FULL_BODY','PUSH','PULL','LEGS','CORE')),
    CONSTRAINT ck_pw_skip_reason CHECK (skip_reason IS NULL OR skip_reason IN
        ('LACK_OF_TIME','FATIGUE','LACK_OF_MOTIVATION','TOO_DIFFICULT',
         'PAIN_OR_DISCOMFORT','SCHEDULE_CHANGE','OTHER')),
    CONSTRAINT ck_pw_duration CHECK (expected_duration_minutes BETWEEN 10 AND 180)
);

COMMENT ON COLUMN planned_workouts.expected_duration_minutes IS
    'Peso de esta sesion en la adherencia ponderada. Lo propone la IA (limitacion declarada).';
COMMENT ON COLUMN planned_workouts.expires_at IS
    '23:59 hora local de scheduled_date. Define que entrenamientos son exigibles.';
COMMENT ON COLUMN planned_workouts.skip_reason IS
    'Insumo directo del modificador de ajuste (18.3). La app debe pedirlo activamente.';

CREATE INDEX ix_planned_workouts_date   ON planned_workouts (plan_id, scheduled_date);
CREATE INDEX ix_planned_workouts_expiry ON planned_workouts (expires_at) WHERE status = 'SCHEDULED';


CREATE TABLE planned_workout_exercises (
    planned_exercise_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    planned_workout_id       BIGINT       NOT NULL,
    exercise_id              BIGINT       NOT NULL,
    exercise_order           SMALLINT     NOT NULL,
    prescription_type        VARCHAR(25)  NOT NULL,
    planned_sets             SMALLINT,
    planned_reps             SMALLINT,
    planned_duration_seconds INTEGER,
    target_load_kg           NUMERIC(7,2),
    rest_seconds             SMALLINT,
    notes                    VARCHAR(300),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_pwe_workout
        FOREIGN KEY (planned_workout_id) REFERENCES planned_workouts (planned_workout_id) ON DELETE CASCADE,
    CONSTRAINT fk_pwe_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (exercise_id),

    CONSTRAINT uq_pwe_order UNIQUE (planned_workout_id, exercise_order),
    CONSTRAINT ck_pwe_type  CHECK (prescription_type IN ('SETS_REPS','DURATION')),
    CONSTRAINT ck_pwe_coherence CHECK (
        (prescription_type = 'SETS_REPS'
            AND planned_sets IS NOT NULL AND planned_reps IS NOT NULL)
        OR
        (prescription_type = 'DURATION'
            AND planned_duration_seconds IS NOT NULL)
    ),
    -- Pisos de la seccion 18.4, aplicados en la base
    CONSTRAINT ck_pwe_min_sets     CHECK (planned_sets IS NULL OR planned_sets >= 2),
    CONSTRAINT ck_pwe_min_reps     CHECK (planned_reps IS NULL OR planned_reps >= 6),
    CONSTRAINT ck_pwe_min_duration CHECK (planned_duration_seconds IS NULL OR planned_duration_seconds >= 20)
);

COMMENT ON TABLE planned_workout_exercises IS
    'Lo que el sistema indico. Nunca se escribe aqui el resultado real.';
