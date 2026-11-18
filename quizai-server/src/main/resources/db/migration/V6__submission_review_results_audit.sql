-- TASK-003 P2-F: eight tables; circular FK consistency and immutable history.
CREATE TABLE submission_candidates (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    kind TEXT NOT NULL CHECK(kind IN ('CLIENT_ON_TIME','SERVER_TIMEOUT','CLIENT_LATE')),
    server_received_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ NOT NULL,
    source_revision BIGINT CHECK(source_revision IS NULL OR source_revision>=0),
    payload_digest TEXT NOT NULL CHECK(length(btrim(payload_digest))>0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_candidate_attempt_id UNIQUE(attempt_id,id)
);
-- Mutually exclusive canonical base; one canonical late payload.
CREATE UNIQUE INDEX uq_candidate_base_per_attempt ON submission_candidates(attempt_id)
  WHERE kind IN ('CLIENT_ON_TIME','SERVER_TIMEOUT');
CREATE UNIQUE INDEX uq_candidate_late_per_attempt ON submission_candidates(attempt_id)
  WHERE kind='CLIENT_LATE';
CREATE INDEX idx_candidates_attempt_kind ON submission_candidates(attempt_id,kind);

-- DBX-16: a candidate for another attempt MUST NOT be assignable as effective.
ALTER TABLE attempts ADD CONSTRAINT fk_attempt_effective_candidate_same_attempt
    FOREIGN KEY(id,effective_candidate_id) REFERENCES submission_candidates(attempt_id,id)
    ON DELETE RESTRICT DEFERRABLE INITIALLY IMMEDIATE;

CREATE TABLE submission_candidate_answers (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES submission_candidates(id) ON DELETE RESTRICT,
    question_snapshot_id UUID NOT NULL REFERENCES assignment_question_snapshots(id) ON DELETE RESTRICT,
    selected_option_id UUID NOT NULL REFERENCES assignment_snapshot_options(id) ON DELETE RESTRICT,
    CONSTRAINT uq_candidate_answer_question UNIQUE(candidate_id,question_snapshot_id),
    CONSTRAINT fk_candidate_answer_option_same_question FOREIGN KEY(selected_option_id,question_snapshot_id)
      REFERENCES assignment_snapshot_options(id,question_snapshot_id) ON DELETE RESTRICT
);
CREATE INDEX idx_candidate_answers_candidate ON submission_candidate_answers(candidate_id);

CREATE FUNCTION quizai_check_candidate_answer_assignment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM submission_candidates c JOIN attempts t ON t.id=c.attempt_id
    JOIN assignment_recipients r ON r.id=t.recipient_id
    JOIN assignment_question_snapshots q ON q.assignment_id=r.assignment_id
    WHERE c.id=NEW.candidate_id AND q.id=NEW.question_snapshot_id
  ) THEN
    RAISE EXCEPTION 'Candidate answer question must belong to attempt assignment' USING ERRCODE='23503';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_candidate_answer_assignment BEFORE INSERT ON submission_candidate_answers
FOR EACH ROW EXECUTE FUNCTION quizai_check_candidate_answer_assignment();

CREATE TABLE late_submission_reviews (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES submission_candidates(id) ON DELETE RESTRICT,
    teacher_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decision TEXT NOT NULL CHECK(decision IN ('ACCEPT','REJECT')),
    reason TEXT NOT NULL CHECK(length(btrim(reason))>0),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_late_review_candidate UNIQUE(candidate_id)
);
CREATE INDEX idx_late_reviews_teacher_time ON late_submission_reviews(teacher_id,reviewed_at DESC);

CREATE TABLE submission_scores (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES submission_candidates(id) ON DELETE RESTRICT,
    earned_points INTEGER NOT NULL CHECK(earned_points>=0),
    possible_points INTEGER NOT NULL CHECK(possible_points>0),
    normalized_score NUMERIC(12,8) NOT NULL CHECK(normalized_score>=0 AND normalized_score<=1),
    scored_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submission_score_candidate UNIQUE(candidate_id),
    CONSTRAINT ck_submission_score_bounds CHECK(earned_points<=possible_points),
    CONSTRAINT ck_submission_score_ratio CHECK(normalized_score=round(earned_points::NUMERIC/possible_points,8))
);

CREATE TABLE attempt_result_events (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    old_candidate_id UUID REFERENCES submission_candidates(id) ON DELETE RESTRICT,
    new_candidate_id UUID NOT NULL REFERENCES submission_candidates(id) ON DELETE RESTRICT,
    reason_code TEXT NOT NULL CHECK(length(btrim(reason_code))>0),
    actor_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_old_candidate_attempt FOREIGN KEY(attempt_id,old_candidate_id)
      REFERENCES submission_candidates(attempt_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_new_candidate_attempt FOREIGN KEY(attempt_id,new_candidate_id)
      REFERENCES submission_candidates(attempt_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_attempt_result_events_time ON attempt_result_events(attempt_id,changed_at,id);

CREATE TABLE assignment_recipient_score_events (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES assignment_recipients(id) ON DELETE RESTRICT,
    old_best_attempt_id UUID REFERENCES attempts(id) ON DELETE RESTRICT,
    new_best_attempt_id UUID REFERENCES attempts(id) ON DELETE RESTRICT,
    old_earned INTEGER,
    old_possible INTEGER,
    new_earned INTEGER,
    new_possible INTEGER,
    cause_result_event_id UUID REFERENCES attempt_result_events(id) ON DELETE RESTRICT,
    actor_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_recipient_old_score_pair CHECK(
        (old_earned IS NULL AND old_possible IS NULL) OR
        (old_earned IS NOT NULL AND old_possible IS NOT NULL
         AND old_earned>=0 AND old_possible>0 AND old_earned<=old_possible)
    ),
    CONSTRAINT ck_recipient_new_score_pair CHECK(
        (new_earned IS NULL AND new_possible IS NULL) OR
        (new_earned IS NOT NULL AND new_possible IS NOT NULL
         AND new_earned>=0 AND new_possible>0 AND new_earned<=new_possible)
    )
);
CREATE INDEX idx_recipient_score_events_time ON assignment_recipient_score_events(recipient_id,changed_at,id);

CREATE TABLE idempotency_receipts (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE RESTRICT,
    operation TEXT NOT NULL CHECK(operation IN ('AUTOSAVE','CLIENT_ON_TIME','CLIENT_LATE','TRANSFER','TIMEOUT')),
    request_id TEXT NOT NULL CHECK(length(btrim(request_id))>0),
    payload_digest TEXT NOT NULL CHECK(length(btrim(payload_digest))>0),
    durable_received_at TIMESTAMPTZ NOT NULL,
    state TEXT NOT NULL CHECK(state IN ('PENDING','COMPLETE','REJECTED')),
    response_ref TEXT,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_idempotency_attempt_request UNIQUE(attempt_id,operation,request_id),
    CONSTRAINT ck_receipt_processed_time CHECK(processed_at IS NULL OR processed_at>=durable_received_at)
);
CREATE INDEX idx_receipts_pending ON idempotency_receipts(state,durable_received_at) WHERE state='PENDING';

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    assignment_id UUID REFERENCES assignments(id) ON DELETE RESTRICT,
    attempt_id UUID REFERENCES attempts(id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL CHECK(length(btrim(event_type))>0),
    event_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    outcome TEXT NOT NULL CHECK(length(btrim(outcome))>0),
    metadata_sanitized JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_audit_metadata_object CHECK(jsonb_typeof(metadata_sanitized)='object')
);
CREATE INDEX idx_audit_attempt_time ON audit_events(attempt_id,event_at DESC);
CREATE INDEX idx_audit_actor_time ON audit_events(actor_id,event_at DESC);

CREATE FUNCTION quizai_guard_late_review_kind() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM submission_candidates WHERE id=NEW.candidate_id AND kind='CLIENT_LATE') THEN
      RAISE EXCEPTION 'Review requires CLIENT_LATE candidate' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_late_review_kind BEFORE INSERT ON late_submission_reviews
FOR EACH ROW EXECUTE FUNCTION quizai_guard_late_review_kind();

-- Strict append-only data. Important: do not use these tables to keep working autosave state.
CREATE FUNCTION quizai_append_only() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Immutable audit/submission history: %',TG_TABLE_NAME USING ERRCODE='23514';
END $$;
CREATE TRIGGER trg_candidates_immutable BEFORE UPDATE OR DELETE ON submission_candidates
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_candidate_answers_immutable BEFORE UPDATE OR DELETE ON submission_candidate_answers
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_scores_immutable BEFORE UPDATE OR DELETE ON submission_scores
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_late_reviews_immutable BEFORE UPDATE OR DELETE ON late_submission_reviews
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_result_events_immutable BEFORE UPDATE OR DELETE ON attempt_result_events
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_recipient_score_events_immutable BEFORE UPDATE OR DELETE ON assignment_recipient_score_events
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_publication_events_immutable BEFORE UPDATE OR DELETE ON assignment_publication_events
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();
CREATE TRIGGER trg_ai_messages_immutable BEFORE UPDATE OR DELETE ON ai_messages
FOR EACH ROW EXECUTE FUNCTION quizai_append_only();

-- Key business protections not reducible to CHECK constraints remain service responsibilities:
-- owner/role authorization, teacher review transaction, timeout worker and binding/token validation.
