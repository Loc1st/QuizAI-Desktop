-- TASK-003 P2-E: four tables. Circular FK is installed at end of V6.
CREATE TABLE attempts (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES assignment_recipients(id) ON DELETE RESTRICT,
    attempt_no INTEGER NOT NULL CHECK(attempt_no>=1),
    start_request_id TEXT NOT NULL CHECK(length(btrim(start_request_id))>0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline_at TIMESTAMPTZ NOT NULL,
    phase TEXT NOT NULL DEFAULT 'IN_PROGRESS' CHECK(phase IN ('IN_PROGRESS','CLOSED')),
    last_applied_revision BIGINT NOT NULL DEFAULT 0 CHECK(last_applied_revision>=0),
    effective_candidate_id UUID,
    CONSTRAINT uq_attempts_recipient_no UNIQUE(recipient_id,attempt_no),
    CONSTRAINT uq_attempts_start_id UNIQUE(recipient_id,start_request_id),
    CONSTRAINT ck_attempt_deadline CHECK(deadline_at>started_at)
);
CREATE INDEX idx_attempts_phase_deadline ON attempts(phase,deadline_at);
CREATE INDEX idx_attempts_recipient ON attempts(recipient_id,attempt_no);

CREATE TABLE attempt_answers (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    question_snapshot_id UUID NOT NULL REFERENCES assignment_question_snapshots(id) ON DELETE RESTRICT,
    selected_option_id UUID REFERENCES assignment_snapshot_options(id) ON DELETE RESTRICT,
    last_revision BIGINT NOT NULL CHECK(last_revision>=1),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attempt_answers_question UNIQUE(attempt_id,question_snapshot_id),
    CONSTRAINT fk_attempt_answer_option_question FOREIGN KEY(selected_option_id,question_snapshot_id)
      REFERENCES assignment_snapshot_options(id,question_snapshot_id) ON DELETE RESTRICT
);
CREATE INDEX idx_attempt_answers_attempt_revision ON attempt_answers(attempt_id,last_revision);

CREATE TABLE attempt_device_bindings (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    binding_version INTEGER NOT NULL CHECK(binding_version>=1),
    auth_session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE RESTRICT,
    token_hash TEXT NOT NULL CHECK(length(btrim(token_hash))>0),
    bound_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_attempt_bindings_version UNIQUE(attempt_id,binding_version),
    CONSTRAINT uq_attempt_bindings_token UNIQUE(token_hash),
    CONSTRAINT ck_attempt_binding_time CHECK(revoked_at IS NULL OR revoked_at>=bound_at)
);
CREATE UNIQUE INDEX uq_attempt_bindings_active ON attempt_device_bindings(attempt_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_attempt_bindings_session ON attempt_device_bindings(auth_session_id,attempt_id);

CREATE TABLE device_transfer_requests (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    requested_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    new_auth_session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE RESTRICT,
    status TEXT NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED')),
    reviewed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    review_reason TEXT,
    CONSTRAINT ck_device_transfer_review CHECK (
       (status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
       OR (status<>'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
           AND review_reason IS NOT NULL AND length(btrim(review_reason))>0)
    )
);
CREATE INDEX idx_device_transfer_pending ON device_transfer_requests(attempt_id,status);

CREATE FUNCTION quizai_guard_attempt_answer_assignment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM attempts t
    JOIN assignment_recipients r ON r.id=t.recipient_id
    JOIN assignment_question_snapshots q ON q.assignment_id=r.assignment_id
    WHERE t.id=NEW.attempt_id AND q.id=NEW.question_snapshot_id
  ) THEN
    RAISE EXCEPTION 'Working answer must belong to attempt assignment' USING ERRCODE='23503';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_answer_assignment BEFORE INSERT OR UPDATE ON attempt_answers
FOR EACH ROW EXECUTE FUNCTION quizai_guard_attempt_answer_assignment();

CREATE FUNCTION quizai_guard_attempt_core() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.recipient_id IS DISTINCT FROM OLD.recipient_id
    OR NEW.attempt_no IS DISTINCT FROM OLD.attempt_no OR NEW.start_request_id IS DISTINCT FROM OLD.start_request_id
    OR NEW.started_at IS DISTINCT FROM OLD.started_at OR NEW.deadline_at IS DISTINCT FROM OLD.deadline_at THEN
      RAISE EXCEPTION 'Attempt identity/deadline are immutable' USING ERRCODE='23514';
 END IF;
 IF OLD.phase='CLOSED' AND NEW.phase<>'CLOSED' THEN
      RAISE EXCEPTION 'Closed attempt cannot be reopened' USING ERRCODE='23514';
 END IF;
 IF NEW.last_applied_revision<OLD.last_applied_revision THEN
      RAISE EXCEPTION 'Attempt revision cannot decrease' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_core BEFORE UPDATE ON attempts FOR EACH ROW EXECUTE FUNCTION quizai_guard_attempt_core();
