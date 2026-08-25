-- =====================================================================
-- FitSense MVP 1.0 - V6: Modulo E (Metricas y adaptacion)
-- Diseno: secciones 14, 15 y 16
-- =====================================================================

CREATE TABLE calculation_configs (
    config_id   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version     VARCHAR(30)  NOT NULL,
    description VARCHAR(500) NOT NULL,
    params      JSONB        NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    valid_from  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_config_version UNIQUE (version)
);

COMMENT ON TABLE calculation_configs IS
    'Ninguna constante de calculo vive en el codigo Java. Calibrar no exige recompilar.';

CREATE UNIQUE INDEX ux_config_active ON calculation_configs (is_active) WHERE is_active;


CREATE TABLE weekly_user_metrics (
    weekly_metric_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT       NOT NULL,
    plan_id                 BIGINT,
    week_number             SMALLINT     NOT NULL,
    week_start_date         DATE         NOT NULL,
    week_end_date           DATE         NOT NULL,
    has_active_plan         BOOLEAN      NOT NULL,

    scheduled_workouts      SMALLINT     NOT NULL DEFAULT 0,
    valid_workouts          SMALLINT     NOT NULL DEFAULT 0,
    completed_workouts      SMALLINT     NOT NULL DEFAULT 0,
    skipped_workouts        SMALLINT     NOT NULL DEFAULT 0,
    assigned_exercises      SMALLINT     NOT NULL DEFAULT 0,
    completed_exercises     SMALLINT     NOT NULL DEFAULT 0,

    weighted_adherence_pct  NUMERIC(5,2),
    frequency_adherence_pct NUMERIC(5,2),
    workout_adherence_pct   NUMERIC(5,2),
    exercise_adherence_pct  NUMERIC(5,2),

    total_training_minutes  INTEGER      NOT NULL DEFAULT 0,
    average_session_rpe     NUMERIC(4,2),
    average_satisfaction    NUMERIC(4,2),
    consecutive_skips       SMALLINT     NOT NULL DEFAULT 0,
    days_since_last_workout SMALLINT,
    dominant_skip_reason    VARCHAR(40),

    risk_score              NUMERIC(5,2),
    risk_level              VARCHAR(20),
    risk_factors            JSONB,
    is_dropout              BOOLEAN      NOT NULL DEFAULT FALSE,

    calculation_version     VARCHAR(30)  NOT NULL,
    calculated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_wum_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_wum_plan FOREIGN KEY (plan_id) REFERENCES weekly_training_plans (plan_id),

    CONSTRAINT uq_wum_user_week UNIQUE (user_id, week_start_date),
    CONSTRAINT ck_wum_weighted   CHECK (weighted_adherence_pct  IS NULL OR weighted_adherence_pct  BETWEEN 0 AND 100),
    CONSTRAINT ck_wum_frequency  CHECK (frequency_adherence_pct IS NULL OR frequency_adherence_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_wum_workout    CHECK (workout_adherence_pct   IS NULL OR workout_adherence_pct   BETWEEN 0 AND 100),
    CONSTRAINT ck_wum_exercise   CHECK (exercise_adherence_pct  IS NULL OR exercise_adherence_pct  BETWEEN 0 AND 100),
    CONSTRAINT ck_wum_risk_level CHECK (risk_level IS NULL OR risk_level IN ('LOW','MODERATE','HIGH','CRITICAL')),
    CONSTRAINT ck_wum_skip_reason CHECK (dominant_skip_reason IS NULL OR dominant_skip_reason IN
        ('LACK_OF_TIME','FATIGUE','LACK_OF_MOTIVATION','TOO_DIFFICULT',
         'PAIN_OR_DISCOMFORT','SCHEDULE_CHANGE','OTHER')),
    -- Sin plan activo no hay denominador: la adherencia es NULL, no 0
    CONSTRAINT ck_wum_null_when_no_plan
        CHECK (has_active_plan OR weighted_adherence_pct IS NULL)
);

COMMENT ON TABLE weekly_user_metrics IS
    'Cierre semanal. Es la tabla que se exporta para el analisis de la tesis.';
COMMENT ON COLUMN weekly_user_metrics.weighted_adherence_pct IS
    'Metrica primaria. NULL (no 0) si la semana no llego a generarse: se excluye de los promedios.';
COMMENT ON COLUMN weekly_user_metrics.dominant_skip_reason IS
    'Conecta la medicion con la adaptacion: permite que el ajuste responda a la causa.';

CREATE INDEX ix_weekly_metrics_user ON weekly_user_metrics (user_id, week_start_date);


CREATE TABLE user_interventions (
    intervention_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT        NOT NULL,
    weekly_metric_id         BIGINT        NOT NULL,
    source_plan_id           BIGINT        NOT NULL,
    resulting_plan_id        BIGINT,

    trigger_adherence_pct    NUMERIC(5,2)  NOT NULL,
    trigger_skip_reason      VARCHAR(40),
    adjustment_types         VARCHAR(40)[] NOT NULL,

    target_volume_change_pct NUMERIC(6,2)  NOT NULL,
    previous_week_volume     INTEGER       NOT NULL,
    resulting_week_volume    INTEGER,
    actual_volume_change_pct NUMERIC(6,2),

    avg_sets_change          NUMERIC(4,2),
    avg_reps_change          NUMERIC(4,2),
    exercises_change         SMALLINT,
    duration_change_pct      NUMERIC(6,2),
    days_change              SMALLINT,
    difficulty_change        SMALLINT,
    load_change_pct          NUMERIC(6,2),

    message_shown            VARCHAR(500)  NOT NULL,
    rule_version             VARCHAR(30)   NOT NULL,
    applied_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    adherence_after_pct      NUMERIC(5,2),
    outcome                  VARCHAR(20),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_ui_user     FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_ui_metric   FOREIGN KEY (weekly_metric_id) REFERENCES weekly_user_metrics (weekly_metric_id),
    CONSTRAINT fk_ui_source   FOREIGN KEY (source_plan_id) REFERENCES weekly_training_plans (plan_id),
    CONSTRAINT fk_ui_result   FOREIGN KEY (resulting_plan_id) REFERENCES weekly_training_plans (plan_id),

    -- Un solo ajuste por usuario y semana (limite de 18.4)
    CONSTRAINT uq_ui_metric UNIQUE (weekly_metric_id),

    CONSTRAINT ck_ui_types_domain CHECK (adjustment_types <@ ARRAY[
        'NONE','REDUCE_VOLUME','REDUCE_DURATION','REDUCE_DAYS',
        'LOWER_DIFFICULTY','LOWER_LOAD']::VARCHAR(40)[]),
    CONSTRAINT ck_ui_types_size CHECK (array_length(adjustment_types, 1) BETWEEN 1 AND 4),
    CONSTRAINT ck_ui_none_alone CHECK (
        NOT ('NONE' = ANY(adjustment_types)) OR array_length(adjustment_types, 1) = 1),
    CONSTRAINT ck_ui_outcome CHECK (outcome IS NULL OR outcome IN ('IMPROVED','UNCHANGED','WORSENED','PENDING')),
    CONSTRAINT ck_ui_skip_reason CHECK (trigger_skip_reason IS NULL OR trigger_skip_reason IN
        ('LACK_OF_TIME','FATIGUE','LACK_OF_MOTIVATION','TOO_DIFFICULT',
         'PAIN_OR_DISCOMFORT','SCHEDULE_CHANGE','OTHER'))
);

COMMENT ON TABLE user_interventions IS
    'Evidencia de que el sistema se adapta. Una fila por participante y semana, incluso si el ajuste es NONE.';
COMMENT ON COLUMN user_interventions.target_volume_change_pct IS
    'Lo que la regla ORDENO. Contra actual_volume_change_pct mide si el modelo obedece la restriccion.';

CREATE INDEX ix_interventions_user ON user_interventions (user_id, applied_at DESC);
