-- QUIZAI CR-001 | ERD v2.0 | new, empty quizai_v2_* databases ONLY.
-- NEVER point Flyway v2 at quizai_dev / quizai_test (37-table v1).
CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email_normalized text NOT NULL,
    password_hash text NOT NULL,
    display_name text NOT NULL DEFAULT '',
    role text NOT NULL CHECK (role IN ('STUDENT','TEACHER')),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT users_email_normalized_ck CHECK (
        email_normalized = lower(btrim(email_normalized)) AND length(email_normalized) BETWEEN 3 AND 320
    ),
    CONSTRAINT users_password_hash_nonempty_ck CHECK (length(btrim(password_hash)) >= 20),
    CONSTRAINT users_email_uq UNIQUE (email_normalized)
);

CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    token_hash text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT auth_session_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT auth_session_revoke_ck CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT auth_session_token_ck CHECK (length(btrim(token_hash)) >= 32)
);
CREATE INDEX auth_sessions_user_expiry_idx ON auth_sessions(user_id, expires_at);

CREATE TABLE classes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name text NOT NULL,
    join_code_hmac text,
    join_enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT classes_name_ck CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    CONSTRAINT classes_join_enabled_ck CHECK (NOT join_enabled OR
        (join_code_hmac IS NOT NULL AND length(btrim(join_code_hmac)) >= 32))
);
CREATE UNIQUE INDEX classes_enabled_join_code_uq ON classes(join_code_hmac)
    WHERE join_enabled = true AND join_code_hmac IS NOT NULL;
CREATE INDEX classes_teacher_idx ON classes(teacher_id);

CREATE TABLE class_members (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    student_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    left_at timestamptz,
    CONSTRAINT membership_interval_ck CHECK (left_at IS NULL OR left_at >= joined_at),
    CONSTRAINT membership_identity_uq UNIQUE (id, class_id, student_id)
);
CREATE UNIQUE INDEX class_members_active_uq ON class_members(class_id, student_id)
    WHERE left_at IS NULL;
CREATE INDEX class_members_student_idx ON class_members(student_id, joined_at DESC);

CREATE TABLE subjects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    name text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT subject_code_ck CHECK (length(btrim(code)) BETWEEN 1 AND 64),
    CONSTRAINT subject_name_ck CHECK (length(btrim(name)) BETWEEN 1 AND 200)
);

CREATE TABLE topics (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id uuid NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,
    code text NOT NULL,
    name text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT topic_code_ck CHECK (length(btrim(code)) BETWEEN 1 AND 64),
    CONSTRAINT topic_name_ck CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    CONSTRAINT topics_subject_code_uq UNIQUE (subject_id, code),
    CONSTRAINT topics_subject_identity_uq UNIQUE (subject_id, id)
);
