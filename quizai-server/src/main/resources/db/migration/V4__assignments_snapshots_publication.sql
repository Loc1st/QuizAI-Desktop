-- TASK-003 P2-D: five tables. Snapshots refer to approved question versions but store immutable content.
CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    teacher_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source_quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PUBLISHED','CLOSED','ARCHIVED')),
    opens_at TIMESTAMPTZ NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK(duration_minutes>0),
    max_attempts INTEGER NOT NULL DEFAULT 1 CHECK(max_attempts>=1),
    score_release_at TIMESTAMPTZ,
    answer_release_at TIMESTAMPTZ,
    answer_release_mode TEXT NOT NULL CHECK(answer_release_mode IN ('AFTER_VALID_SUBMISSION','AFTER_ASSIGNMENT_CLOSE')),
    policy_revision BIGINT NOT NULL DEFAULT 0 CHECK(policy_revision>=0),
    CONSTRAINT ck_assignment_window CHECK(opens_at < closes_at),
    CONSTRAINT uq_assignment_id_class UNIQUE(id,class_id)
);
CREATE INDEX idx_assignments_class_window ON assignments(class_id,opens_at,closes_at);
CREATE INDEX idx_assignments_teacher_status ON assignments(teacher_id,status);

-- Referencing original membership EPISODE requires same class and same student; enforce via trigger.
CREATE TABLE assignment_recipients (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    membership_id UUID NOT NULL REFERENCES class_members(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assignment_recipients_student UNIQUE(assignment_id,student_id),
    CONSTRAINT uq_assignment_recipients_id_assignment UNIQUE(id,assignment_id)
);
CREATE INDEX idx_assignment_recipients_membership ON assignment_recipients(membership_id);
CREATE INDEX idx_assignment_recipients_student ON assignment_recipients(student_id,assigned_at DESC);

CREATE TABLE assignment_question_snapshots (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE RESTRICT,
    source_question_version_id UUID REFERENCES question_versions(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK(position>=1),
    stem TEXT NOT NULL CHECK(length(btrim(stem))>0),
    difficulty TEXT NOT NULL CHECK(difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT uq_assignment_snapshot_position UNIQUE(assignment_id,position),
    CONSTRAINT uq_assignment_snapshot_id_assignment UNIQUE(id,assignment_id)
);
CREATE INDEX idx_assignment_snapshot_source_version ON assignment_question_snapshots(source_question_version_id);

CREATE TABLE assignment_snapshot_options (
    id UUID PRIMARY KEY,
    question_snapshot_id UUID NOT NULL REFERENCES assignment_question_snapshots(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK(position>=1),
    option_text TEXT NOT NULL CHECK(length(btrim(option_text))>0),
    is_correct BOOLEAN NOT NULL,
    CONSTRAINT uq_assignment_snapshot_option_position UNIQUE(question_snapshot_id,position),
    CONSTRAINT uq_assignment_snapshot_option_id_question UNIQUE(id,question_snapshot_id)
);
CREATE INDEX idx_assignment_snapshot_options_question ON assignment_snapshot_options(question_snapshot_id,position);

CREATE TABLE assignment_publication_events (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE RESTRICT,
    release_kind TEXT NOT NULL CHECK(release_kind IN ('SCORE','ANSWER')),
    first_available_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    witness_recipient_id UUID,
    policy_revision BIGINT NOT NULL CHECK(policy_revision>=0),
    source_event TEXT NOT NULL CHECK(length(btrim(source_event))>0),
    CONSTRAINT uq_assignment_publication_kind UNIQUE(assignment_id,release_kind),
    CONSTRAINT fk_publication_witness_assignment FOREIGN KEY(witness_recipient_id,assignment_id)
      REFERENCES assignment_recipients(id,assignment_id) ON DELETE RESTRICT
);
CREATE INDEX idx_publication_assignment_kind ON assignment_publication_events(assignment_id,release_kind);

CREATE FUNCTION quizai_guard_assignment_owner() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM classes WHERE id=NEW.class_id AND teacher_id=NEW.teacher_id) THEN
       RAISE EXCEPTION 'Assignment teacher must own class' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM quizzes WHERE id=NEW.source_quiz_id AND owner_id=NEW.teacher_id AND quiz_type='TEACHER_QUIZ') THEN
       RAISE EXCEPTION 'Assignment source quiz must belong to teacher' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_assignment_owner BEFORE INSERT OR UPDATE OF class_id,teacher_id,source_quiz_id
ON assignments FOR EACH ROW EXECUTE FUNCTION quizai_guard_assignment_owner();

CREATE FUNCTION quizai_guard_recipient_membership() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP<>'INSERT' THEN
        RAISE EXCEPTION 'Original assignment recipient and membership episode are immutable' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM class_members m JOIN assignments a ON a.class_id=m.class_id
      WHERE a.id=NEW.assignment_id AND m.id=NEW.membership_id AND m.student_id=NEW.student_id
        AND m.joined_at<=NEW.assigned_at AND (m.left_at IS NULL OR NEW.assigned_at<m.left_at)
    ) THEN
       RAISE EXCEPTION 'Recipient must use active original class membership episode' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_assignment_recipient_membership BEFORE INSERT OR UPDATE OR DELETE ON assignment_recipients
FOR EACH ROW EXECUTE FUNCTION quizai_guard_recipient_membership();

CREATE FUNCTION quizai_guard_published_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE assignment_uuid UUID; assignment_status TEXT;
BEGIN
  IF TG_OP='UPDATE' AND NEW.id IS DISTINCT FROM OLD.id THEN
     RAISE EXCEPTION 'Snapshot identity is immutable' USING ERRCODE='23514';
  END IF;
  IF TG_TABLE_NAME='assignment_question_snapshots' THEN
    IF TG_OP='UPDATE' AND NEW.assignment_id IS DISTINCT FROM OLD.assignment_id THEN
       RAISE EXCEPTION 'Snapshot cannot switch assignment' USING ERRCODE='23514';
    END IF;
    assignment_uuid:=CASE WHEN TG_OP='DELETE' THEN OLD.assignment_id ELSE NEW.assignment_id END;
  ELSE
    IF TG_OP='UPDATE' AND NEW.question_snapshot_id IS DISTINCT FROM OLD.question_snapshot_id THEN
       RAISE EXCEPTION 'Snapshot option cannot switch question' USING ERRCODE='23514';
    END IF;
    SELECT assignment_id INTO assignment_uuid FROM assignment_question_snapshots
      WHERE id=CASE WHEN TG_OP='DELETE' THEN OLD.question_snapshot_id ELSE NEW.question_snapshot_id END;
  END IF;
  -- Serialize every content write against publication on this assignment row.
  SELECT status INTO assignment_status FROM assignments WHERE id=assignment_uuid FOR UPDATE;
  IF NOT FOUND THEN
     RAISE EXCEPTION 'Snapshot assignment does not exist' USING ERRCODE='23503';
  END IF;
  IF assignment_status<>'DRAFT' THEN
     RAISE EXCEPTION 'Published/closed assignment snapshots cannot change' USING ERRCODE='23514';
  END IF;
  RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER trg_snapshot_guard BEFORE INSERT OR UPDATE OR DELETE ON assignment_question_snapshots
FOR EACH ROW EXECUTE FUNCTION quizai_guard_published_snapshot();
CREATE TRIGGER trg_snapshot_option_guard BEFORE INSERT OR UPDATE OR DELETE ON assignment_snapshot_options
FOR EACH ROW EXECUTE FUNCTION quizai_guard_published_snapshot();

CREATE FUNCTION quizai_guard_assignment_publish() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE n INT; bad INT;
BEGIN
 IF TG_OP='INSERT' THEN
   IF NEW.status<>'DRAFT' THEN
       RAISE EXCEPTION 'Create assignment as draft before adding and publishing snapshots' USING ERRCODE='23514';
   END IF;
   RETURN NEW;
 END IF;
 IF OLD.status<>'DRAFT' AND NEW.status='DRAFT' THEN
       RAISE EXCEPTION 'Published assignment cannot return to draft' USING ERRCODE='23514';
 END IF;
 IF OLD.status='DRAFT' AND NEW.status<>'DRAFT' THEN
   SELECT count(*) INTO n FROM assignment_question_snapshots WHERE assignment_id=NEW.id;
   SELECT count(*) INTO bad FROM assignment_question_snapshots sq WHERE sq.assignment_id=NEW.id
      AND (SELECT count(*) FROM assignment_snapshot_options o WHERE o.question_snapshot_id=sq.id)<2
         OR sq.assignment_id=NEW.id AND (SELECT count(*) FROM assignment_snapshot_options o
                 WHERE o.question_snapshot_id=sq.id AND o.is_correct)<>1;
   IF n<1 OR bad>0 THEN
       RAISE EXCEPTION 'Assignment must have >=1 complete question, with >=2 options and exactly 1 correct each' USING ERRCODE='23514';
   END IF;
   IF EXISTS (
      SELECT 1 FROM assignment_question_snapshots sq
      LEFT JOIN question_versions v ON v.id=sq.source_question_version_id
      LEFT JOIN questions q ON q.id=v.question_id
      WHERE sq.assignment_id=NEW.id AND sq.source_question_version_id IS NOT NULL
        AND (v.approval_status IS DISTINCT FROM 'APPROVED' OR q.owner_id IS DISTINCT FROM NEW.teacher_id)
   ) THEN
       RAISE EXCEPTION 'Published snapshot source must be approved and owned by the assignment teacher' USING ERRCODE='23514';
   END IF;
 END IF;
 IF OLD.status<>'DRAFT' AND (NEW.class_id IS DISTINCT FROM OLD.class_id OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
     OR NEW.source_quiz_id IS DISTINCT FROM OLD.source_quiz_id OR NEW.opens_at IS DISTINCT FROM OLD.opens_at
     OR NEW.closes_at IS DISTINCT FROM OLD.closes_at OR NEW.duration_minutes IS DISTINCT FROM OLD.duration_minutes
     OR NEW.max_attempts IS DISTINCT FROM OLD.max_attempts) THEN
       RAISE EXCEPTION 'Published assignment core policy is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_assignment_publish BEFORE INSERT OR UPDATE ON assignments
FOR EACH ROW EXECUTE FUNCTION quizai_guard_assignment_publish();
