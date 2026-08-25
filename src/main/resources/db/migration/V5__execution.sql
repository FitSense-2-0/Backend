-- =====================================================================
-- FitSense MVP 1.0 - V5: Modulo D (Ejecucion)
-- Diseno: secciones 12 y 13
-- Aqui vive lo que el usuario HIZO. Separado de lo planificado.
-- =====================================================================

CREATE TABLE workout_sessions (
    session_id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT       NOT NULL,
    planned_workout_id       BIGINT       NOT NULL,
    plan_id                  BIGINT       NOT NULL,
    attempt_number           SMALLINT     NOT NULL DEFAULT 1,
    counts_toward_adherence  BOOLEAN      NOT NULL DEFAULT TRUE,
    started_at               TIMESTAMPTZ  NOT NULL,
    ended_at                 TIMESTAMPTZ,
    active_minutes           SMALLINT,
    completion_percentage    NUMERIC(5,2) NOT NULL DEFAULT 0,
    session_rpe              SMALLINT,
    satisfaction             SMALLINT,
    source                   VARCHAR(20)  NOT NULL DEFAULT 'APP_TRACKED',
    status                   VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_session_user    FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_session_workout FOREIGN KEY (planned_workout_id) REFERENCES planned_workouts (planned_workout_id),
    CONSTRAINT fk_session_plan    FOREIGN KEY (plan_id) REFERENCES weekly_training_plans (plan_id),

    CONSTRAINT uq_session_attempt UNIQUE (planned_workout_id, attempt_number),
    CONSTRAINT ck_session_rpe          CHECK (session_rpe IS NULL OR session_rpe BETWEEN 1 AND 10),
    CONSTRAINT ck_session_satisfaction CHECK (satisfaction IS NULL OR satisfaction BETWEEN 1 AND 5),
    CONSTRAINT ck_session_completion   CHECK (completion_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_session_source       CHECK (source IN ('APP_TRACKED','USER_REPORTED')),
    CONSTRAINT ck_session_status       CHECK (status IN ('IN_PROGRESS','COMPLETED','PARTIAL','ABANDONED')),
    CONSTRAINT ck_session_ended        CHECK (ended_at IS NULL OR ended_at >= started_at)
);

COMMENT ON COLUMN workout_sessions.counts_toward_adherence IS
    'Regla del intento valido: el ultimo intento finalizado (COMPLETED o PARTIAL).';
COMMENT ON COLUMN workout_sessions.source IS
    'USER_REPORTED cubre "ya lo hice" sobre un entrenamiento vencido. Evita falsos negativos.';
COMMENT ON COLUMN workout_sessions.plan_id IS
    'Version del plan vigente al iniciar la sesion.';

-- No contar dos veces el mismo entrenamiento en la adherencia
CREATE UNIQUE INDEX ux_session_counted
    ON workout_sessions (planned_workout_id)
    WHERE counts_toward_adherence;

-- Un usuario no puede tener dos sesiones simultaneas
CREATE UNIQUE INDEX ux_session_active
    ON workout_sessions (user_id)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX ix_sessions_user_date ON workout_sessions (user_id, started_at DESC);


CREATE TABLE workout_session_exercises (
    session_exercise_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id              BIGINT       NOT NULL,
    planned_exercise_id     BIGINT       NOT NULL,
    actual_sets             SMALLINT,
    actual_reps_total       INTEGER,
    actual_duration_seconds INTEGER,
    actual_load_kg          NUMERIC(7,2),
    completion_percentage   NUMERIC(5,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(20)  NOT NULL,
    skip_reason             VARCHAR(40),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_wse_session
        FOREIGN KEY (session_id) REFERENCES workout_sessions (session_id) ON DELETE CASCADE,
    CONSTRAINT fk_wse_planned_exercise
        FOREIGN KEY (planned_exercise_id) REFERENCES planned_workout_exercises (planned_exercise_id),

    -- Evita duplicados cuando la app reintenta un envio
    CONSTRAINT uq_wse_session_exercise UNIQUE (session_id, planned_exercise_id),
    CONSTRAINT ck_wse_completion  CHECK (completion_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_wse_status      CHECK (status IN ('COMPLETED','PARTIAL','SKIPPED')),
    CONSTRAINT ck_wse_skip_reason CHECK (skip_reason IS NULL OR skip_reason IN
        ('LACK_OF_TIME','FATIGUE','LACK_OF_MOTIVATION','TOO_DIFFICULT',
         'PAIN_OR_DISCOMFORT','SCHEDULE_CHANGE','OTHER')),
    CONSTRAINT ck_wse_non_negative CHECK (
        (actual_sets IS NULL OR actual_sets >= 0) AND
        (actual_reps_total IS NULL OR actual_reps_total >= 0) AND
        (actual_duration_seconds IS NULL OR actual_duration_seconds >= 0)
    )
);

COMMENT ON TABLE workout_session_exercises IS
    'Esta tabla contra planned_workout_exercises es toda la adherencia. Lo demas son agregaciones.';
COMMENT ON COLUMN workout_session_exercises.actual_reps_total IS
    'Suma de repeticiones. Se compara directo contra planned_sets x planned_reps. No hay tabla workout_sets.';
