-- V2-D: assignments, immutable snapshots, online attempts and audit.
CREATE TABLE assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    teacher_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    quiz_id uuid NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
    title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    opens_at timestamptz NOT NULL,
    closes_at timestamptz NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    max_attempts integer NOT NULL CHECK (max_attempts BETWEEN 1 AND 100),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    score_release_at timestamptz,
    answer_release_at timestamptz,
    answer_release_mode text NOT NULL DEFAULT 'AFTER_ASSIGNMENT_CLOSE'
        CHECK (answer_release_mode IN ('AFTER_VALID_SUBMISSION','AFTER_ASSIGNMENT_CLOSE')),
    score_first_available_at timestamptz,
    answer_first_available_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT assignment_window_ck CHECK (opens_at < closes_at),
    CONSTRAINT assignment_availability_ck CHECK (
        (score_first_available_at IS NULL OR score_release_at IS NOT NULL)
        AND (answer_first_available_at IS NULL OR answer_release_at IS NOT NULL)
    ),
    CONSTRAINT assignment_id_class_uq UNIQUE (id, class_id),
    CONSTRAINT assignment_id_teacher_uq UNIQUE (id, teacher_id)
);
CREATE INDEX assignments_class_window_idx ON assignments(class_id, opens_at, closes_at);
CREATE INDEX assignments_teacher_idx ON assignments(teacher_id, created_at DESC);

CREATE TABLE assignment_recipients (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id uuid NOT NULL REFERENCES assignments(id) ON DELETE RESTRICT,
    student_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    membership_id uuid NOT NULL REFERENCES class_members(id) ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT recipient_student_uq UNIQUE (assignment_id, student_id),
    CONSTRAINT recipient_assignment_id_uq UNIQUE (assignment_id, id)
);
CREATE INDEX recipients_student_idx ON assignment_recipients(student_id, assigned_at DESC);

CREATE TABLE assignment_questions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id uuid NOT NULL REFERENCES assignments(id) ON DELETE RESTRICT,
    source_question_version_id uuid REFERENCES question_versions(id) ON DELETE RESTRICT,
    position integer NOT NULL CHECK (position >= 1),
    snapshot_json jsonb NOT NULL,
    CONSTRAINT assignment_questions_position_uq UNIQUE (assignment_id, position),
    CONSTRAINT assignment_questions_id_assignment_uq UNIQUE (assignment_id, id),
    CONSTRAINT assignment_snapshot_object_ck CHECK (jsonb_typeof(snapshot_json) = 'object')
);
CREATE INDEX assignment_questions_order_idx ON assignment_questions(assignment_id, position);

CREATE TABLE attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id uuid NOT NULL,
    recipient_id uuid NOT NULL REFERENCES assignment_recipients(id) ON DELETE RESTRICT,
    attempt_no integer NOT NULL CHECK (attempt_no >= 1),
    start_request_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS','SUBMITTED','TIMED_OUT')),
    started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    deadline_at timestamptz NOT NULL,
    submitted_at timestamptz,
    submit_request_id uuid,
    submitted_digest text,
    earned_points integer,
    possible_points integer,
    normalized_score numeric(9,6),
    CONSTRAINT attempt_recipient_assignment_fk FOREIGN KEY (assignment_id, recipient_id)
        REFERENCES assignment_recipients(assignment_id, id) ON DELETE RESTRICT,
    CONSTRAINT attempt_no_uq UNIQUE (recipient_id, attempt_no),
    CONSTRAINT attempt_start_request_uq UNIQUE (recipient_id, start_request_id),
    CONSTRAINT attempt_deadline_ck CHECK (deadline_at > started_at),
    CONSTRAINT attempt_result_ck CHECK (
        (status = 'IN_PROGRESS' AND submitted_at IS NULL AND submit_request_id IS NULL
            AND submitted_digest IS NULL AND earned_points IS NULL
            AND possible_points IS NULL AND normalized_score IS NULL)
        OR (status IN ('SUBMITTED','TIMED_OUT') AND submitted_at IS NOT NULL
            AND earned_points IS NOT NULL AND possible_points IS NOT NULL AND normalized_score IS NOT NULL
            AND earned_points >= 0 AND possible_points > 0
            AND earned_points <= possible_points AND normalized_score BETWEEN 0 AND 1
            AND normalized_score = round(earned_points::numeric / nullif(possible_points,0), 6))
    ),
    CONSTRAINT attempt_submit_digest_ck CHECK (
        (status = 'SUBMITTED' AND submit_request_id IS NOT NULL AND submitted_digest IS NOT NULL
            AND submitted_digest ~ '^[0-9a-f]{64}$')
        OR (status <> 'SUBMITTED' AND submit_request_id IS NULL AND submitted_digest IS NULL)
    )
);
CREATE INDEX attempt_deadlines_idx ON attempts(deadline_at) WHERE status = 'IN_PROGRESS';
CREATE INDEX attempt_recipient_scores_idx ON attempts(recipient_id, status, normalized_score DESC);

CREATE TABLE attempt_answers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id uuid NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    assignment_question_id uuid NOT NULL REFERENCES assignment_questions(id) ON DELETE RESTRICT,
    selected_option_key text,
    answer_revision bigint NOT NULL CHECK (answer_revision >= 0),
    server_saved_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT attempt_answers_one_question_uq UNIQUE (attempt_id, assignment_question_id),
    CONSTRAINT attempt_answer_key_ck CHECK (selected_option_key IS NULL OR length(btrim(selected_option_key)) > 0)
);
CREATE INDEX attempt_answers_attempt_idx ON attempt_answers(attempt_id);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    event_type text NOT NULL CHECK (length(btrim(event_type)) > 0),
    resource_type text NOT NULL CHECK (length(btrim(resource_type)) > 0),
    resource_id uuid,
    outcome text NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILED')),
    metadata_sanitized jsonb,
    event_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT audit_event_metadata_ck CHECK (metadata_sanitized IS NULL OR jsonb_typeof(metadata_sanitized) = 'object')
);
CREATE INDEX audit_events_recent_idx ON audit_events(event_at DESC);
CREATE INDEX audit_events_actor_idx ON audit_events(actor_id, event_at DESC);
