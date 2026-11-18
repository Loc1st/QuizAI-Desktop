-- QuizAI Desktop TASK-003 DB-05 — P2-A, PostgreSQL 16+ compatible baseline syntax.
-- Source: approved ERD v1.0 SHA-256 5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab
-- SIX TABLES ONLY.  No sample users, no seeds, no ALTER ROLE, no CREATE DATABASE.
-- Before running: P1 gates and explicit DEV/TEST authorization. Flyway remains disabled by default.
-- UUIDs are generated on the application/server; no DB-side extension required.
-- No cascading deletes: preserve identities/membership episodes for audit and issued assignments.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL,
    status          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_email_normalized CHECK (
        email = lower(btrim(email)) AND length(email) > 0
    ),
    CONSTRAINT ck_users_password_hash_nonempty CHECK (length(btrim(password_hash)) > 0),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'TEACHER')),
    CONSTRAINT ck_users_status_nonempty CHECK (length(btrim(status)) > 0),
    CONSTRAINT ck_users_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE auth_sessions (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    session_token_hash  TEXT NOT NULL,
    device_session_id   TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    last_seen_at        TIMESTAMPTZ,
    CONSTRAINT uq_auth_sessions_token_hash UNIQUE (session_token_hash),
    CONSTRAINT ck_auth_sessions_hash CHECK (length(btrim(session_token_hash)) > 0),
    CONSTRAINT ck_auth_sessions_device CHECK (length(btrim(device_session_id)) > 0),
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_sessions_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_auth_sessions_last_seen CHECK (last_seen_at IS NULL OR last_seen_at >= created_at)
);

CREATE TABLE classes (
    id                UUID PRIMARY KEY,
    teacher_id        UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name              TEXT NOT NULL,
    join_code_digest  TEXT,
    join_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    status            TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_classes_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_classes_status CHECK (length(btrim(status)) > 0),
    CONSTRAINT ck_classes_digest CHECK (join_code_digest IS NULL OR length(btrim(join_code_digest)) > 0),
    CONSTRAINT ck_classes_enabled_code CHECK (NOT join_enabled OR join_code_digest IS NOT NULL)
);

-- A disabled/rotated code may be retained elsewhere; uniqueness is ONLY among enabled codes.
-- App must calculate a keyed HMAC digest, not unkeyed SHA or plaintext join codes.
CREATE UNIQUE INDEX uq_classes_enabled_join_code_digest
    ON classes (join_code_digest) WHERE join_enabled IS TRUE;
CREATE INDEX idx_classes_teacher_status ON classes (teacher_id, status);

CREATE TABLE class_members (
    id          UUID PRIMARY KEY,
    class_id    UUID NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    student_id  UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at   TIMESTAMPTZ NOT NULL,
    left_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_class_members_interval CHECK (left_at IS NULL OR left_at > joined_at)
);

-- A student can only have one ACTIVE membership episode per class.
CREATE UNIQUE INDEX uq_class_members_active
    ON class_members (class_id, student_id) WHERE left_at IS NULL;
CREATE INDEX idx_class_members_student_history
    ON class_members (student_id, class_id, joined_at, left_at);
CREATE INDEX idx_class_members_class_history
    ON class_members (class_id, joined_at);

-- Historical membership episodes are append/close only: no reassignment, re-open, or backdating.
-- Changing left_at from NULL to a legitimate closing time is allowed exactly once.
CREATE FUNCTION quizai_guard_membership_episode()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.class_id IS DISTINCT FROM OLD.class_id
        OR NEW.student_id IS DISTINCT FROM OLD.student_id
        OR NEW.joined_at IS DISTINCT FROM OLD.joined_at
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION 'Membership episode identity and start are immutable'
            USING ERRCODE = '23514';
    END IF;

    IF OLD.left_at IS NOT NULL AND NEW.left_at IS DISTINCT FROM OLD.left_at THEN
        RAISE EXCEPTION 'Closed membership episode cannot be reopened or changed'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_class_members_episode_guard
BEFORE UPDATE ON class_members
FOR EACH ROW EXECUTE FUNCTION quizai_guard_membership_episode();

CREATE TABLE subjects (
    id               UUID PRIMARY KEY,
    code             TEXT NOT NULL,
    name             TEXT NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    catalog_version  INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_subjects_code UNIQUE (code),
    CONSTRAINT ck_subjects_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_subjects_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_subjects_catalog_version CHECK (catalog_version >= 1)
);

CREATE TABLE topics (
    id          UUID PRIMARY KEY,
    subject_id  UUID NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,
    code        TEXT NOT NULL,
    name        TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_topics_subject_code UNIQUE (subject_id, code),
    CONSTRAINT ck_topics_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_topics_name CHECK (length(btrim(name)) > 0)
);
CREATE INDEX idx_topics_subject_active ON topics (subject_id, active);

-- DBX-03 role safety: checking that classes.teacher_id is TEACHER and
-- class_members.student_id is STUDENT requires application transaction guard
-- until role consistency is intentionally introduced as an additional DB constraint.
-- Do not read this migration as implementing service-level authorization.
