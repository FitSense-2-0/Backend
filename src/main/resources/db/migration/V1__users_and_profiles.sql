-- =====================================================================
-- FitSense MVP 1.0 - V1: Modulo A (Cuenta y perfil)
-- Diseno: secciones 4 y 5
-- =====================================================================

-- ---------------------------------------------------------------------
-- users: cuenta + condicion de participante del estudio
-- ---------------------------------------------------------------------
CREATE TABLE users (
                       user_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       email                VARCHAR(254) NOT NULL,
                       password_hash        VARCHAR(255) NOT NULL,
                       first_name           VARCHAR(100) NOT NULL,
                       last_name            VARCHAR(150),
                       timezone             VARCHAR(64)  NOT NULL DEFAULT 'America/Lima',
                       status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- Consentimiento: dos columnas, no una tabla (se otorga una sola vez)
                       consent_granted_at   TIMESTAMPTZ,
                       consent_version      VARCHAR(20),

    -- Participacion en el estudio
                       is_study_participant BOOLEAN      NOT NULL DEFAULT TRUE,
                       participant_code     VARCHAR(20),
                       enrolled_at          TIMESTAMPTZ,
                       withdrawn_at         TIMESTAMPTZ,

                       last_login_at        TIMESTAMPTZ,
                       created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

                       CONSTRAINT uq_users_email            UNIQUE (email),
                       CONSTRAINT uq_users_participant_code UNIQUE (participant_code),
                       CONSTRAINT ck_users_status           CHECK (status IN ('ACTIVE','INACTIVE','DELETED')),
                       CONSTRAINT ck_users_email_lower      CHECK (email = lower(email))
);

COMMENT ON COLUMN users.timezone IS
    'Zona IANA. La semana cierra el lunes 03:00 hora local; metric_date es fecha local.';
COMMENT ON COLUMN users.is_study_participant IS
    'false para cuentas de prueba del equipo. Toda consulta de analisis filtra por true.';
COMMENT ON COLUMN users.withdrawn_at IS
    'Retiro voluntario: se detiene la generacion de planes, no se borran los datos ya recolectados.';

-- Indice para el filtro de analisis
CREATE INDEX ix_users_study ON users (is_study_participant) WHERE is_study_participant;


-- ---------------------------------------------------------------------
-- user_profiles: todo lo que la IA necesita para generar el plan
-- Absorbe user_goals, user_availability, user_equipment y
-- user_training_constraints del diseno 2.0
-- ---------------------------------------------------------------------
CREATE TABLE user_profiles (
                               user_id              BIGINT        PRIMARY KEY,
                               birth_date           DATE          NOT NULL,
                               biological_sex       VARCHAR(20),
                               height_cm            NUMERIC(5,2)  NOT NULL,
                               current_weight_kg    NUMERIC(6,2)  NOT NULL,
                               target_weight_kg     NUMERIC(6,2),
                               fitness_level        VARCHAR(20)   NOT NULL,
                               goal_type            VARCHAR(30)   NOT NULL,
                               goal_text            VARCHAR(500)  NOT NULL,
                               training_location    VARCHAR(20)   NOT NULL,
                               days_per_week        SMALLINT      NOT NULL,
                               available_days       SMALLINT[]    NOT NULL,
                               session_minutes      SMALLINT      NOT NULL,
                               equipment_codes      VARCHAR(40)[] NOT NULL DEFAULT '{}',
                               blocked_exercise_ids BIGINT[]      NOT NULL DEFAULT '{}',
                               health_notes         VARCHAR(500),
                               created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
                               updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

                               CONSTRAINT fk_user_profiles_user
                                   FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,

                               CONSTRAINT ck_profile_days_per_week   CHECK (days_per_week BETWEEN 1 AND 7),
                               CONSTRAINT ck_profile_available_days  CHECK (array_length(available_days, 1) >= days_per_week),
                               CONSTRAINT ck_profile_days_range      CHECK (available_days <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]),
    CONSTRAINT ck_profile_session_minutes CHECK (session_minutes BETWEEN 15 AND 180),
    CONSTRAINT ck_profile_height          CHECK (height_cm BETWEEN 120 AND 250),
    CONSTRAINT ck_profile_weight          CHECK (current_weight_kg BETWEEN 30 AND 300),
    CONSTRAINT ck_profile_target_weight   CHECK (target_weight_kg IS NULL OR target_weight_kg BETWEEN 30 AND 300),
    CONSTRAINT ck_profile_birth_date      CHECK (birth_date < CURRENT_DATE),
    CONSTRAINT ck_profile_sex             CHECK (biological_sex IS NULL OR biological_sex IN ('MALE','FEMALE','OTHER')),
    CONSTRAINT ck_profile_fitness_level   CHECK (fitness_level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT ck_profile_goal_type       CHECK (goal_type IN ('LOSE_WEIGHT','GAIN_MUSCLE','INCREASE_STRENGTH','GENERAL_FITNESS')),
    CONSTRAINT ck_profile_location        CHECK (training_location IN ('HOME','GYM','MIXED'))
);

COMMENT ON COLUMN user_profiles.days_per_week IS
    'Entero exacto. Es el denominador base de la adherencia; nunca un rango.';
COMMENT ON COLUMN user_profiles.current_weight_kg IS
    'Se sobrescribe. El MVP no conserva historial de peso (decision consciente, seccion 5).';
COMMENT ON COLUMN user_profiles.goal_text IS
    'Objetivo en texto libre del usuario. Va al prompt de la IA.';
COMMENT ON COLUMN user_profiles.health_notes IS
    'Texto libre. Lo interpreta la IA, no una consulta SQL. No reemplaza evaluacion medica.';

-- Permite la consulta inversa "que usuarios tienen mancuernas"
CREATE INDEX ix_user_profiles_equipment ON user_profiles USING GIN (equipment_codes);
