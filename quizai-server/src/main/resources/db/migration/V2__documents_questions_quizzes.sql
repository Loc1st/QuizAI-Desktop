-- TASK-003 P2-B: Nine MVP tables. Source: approved ERD v1.0; no AI-after-assigned-exam.
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    input_type TEXT NOT NULL CHECK (input_type IN ('PDF','DOCX','TXT','PASTED_TEXT')),
    storage_key TEXT,
    filename TEXT,
    mime_type TEXT,
    size_bytes BIGINT CHECK (size_bytes IS NULL OR size_bytes >= 0),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (storage_key IS NULL OR length(btrim(storage_key)) > 0)
);
CREATE INDEX idx_documents_owner_active ON documents(owner_id,created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE document_extractions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE RESTRICT,
    extraction_no INTEGER NOT NULL CHECK (extraction_no >= 1),
    text_storage_key TEXT,
    checksum TEXT NOT NULL CHECK (length(btrim(checksum)) > 0),
    extractor_version TEXT NOT NULL CHECK (length(btrim(extractor_version)) > 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING','PROCESSING','COMPLETE','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_extractions_doc_no UNIQUE(document_id, extraction_no),
    CONSTRAINT uq_document_extractions_doc_id UNIQUE(document_id,id),
    CHECK (status <> 'COMPLETE' OR text_storage_key IS NOT NULL)
);
CREATE INDEX idx_document_extractions_doc ON document_extractions(document_id, extraction_no DESC);

-- A completed extraction is a versioned source, not mutable working state.
CREATE FUNCTION quizai_guard_complete_extraction() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status='COMPLETE' THEN
        RAISE EXCEPTION 'Completed document extraction is immutable' USING ERRCODE='23514';
    END IF;
    RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER trg_document_extraction_complete BEFORE UPDATE OR DELETE ON document_extractions
FOR EACH ROW EXECUTE FUNCTION quizai_guard_complete_extraction();

CREATE TABLE ai_generations (
    id UUID PRIMARY KEY,
    requested_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source_document_id UUID REFERENCES documents(id) ON DELETE RESTRICT,
    source_extraction_id UUID REFERENCES document_extractions(id) ON DELETE RESTRICT,
    request_type TEXT NOT NULL CHECK (request_type IN ('QUESTION_GENERATION','PDF_TUTOR_CONTEXT')),
    status TEXT NOT NULL CHECK (status IN ('PENDING','PROCESSING','COMPLETE','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_generations_requester ON ai_generations(requested_by,created_at DESC);

CREATE TABLE question_banks (
    id UUID PRIMARY KEY,
    owner_teacher_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name TEXT NOT NULL CHECK(length(btrim(name))>0),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_question_banks_teacher_active ON question_banks(owner_teacher_id) WHERE archived_at IS NULL;

CREATE TABLE questions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    bank_id UUID REFERENCES question_banks(id) ON DELETE RESTRICT,
    source_generation_id UUID REFERENCES ai_generations(id) ON DELETE RESTRICT,
    source_document_id UUID REFERENCES documents(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_questions_owner_bank ON questions(owner_id, bank_id);

-- For a topic selection, ensure it belongs to the selected subject without application-only trust.
ALTER TABLE topics ADD CONSTRAINT uq_topics_subject_id UNIQUE (subject_id,id);
CREATE TABLE question_versions (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    version_no INTEGER NOT NULL CHECK(version_no >= 1),
    stem TEXT NOT NULL CHECK(length(btrim(stem)) > 0),
    subject_id UUID REFERENCES subjects(id) ON DELETE RESTRICT,
    topic_id UUID REFERENCES topics(id) ON DELETE RESTRICT,
    difficulty TEXT NOT NULL CHECK(difficulty IN ('EASY','MEDIUM','HARD')),
    approval_status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(approval_status IN ('DRAFT','APPROVED','REJECTED')),
    approved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    approved_at TIMESTAMPTZ,
    CONSTRAINT uq_question_versions_question_no UNIQUE(question_id,version_no),
    CONSTRAINT uq_question_versions_id_question UNIQUE(id,question_id),
    CONSTRAINT ck_question_version_topic_subject CHECK(topic_id IS NULL OR subject_id IS NOT NULL),
    CONSTRAINT fk_question_version_topic_subject FOREIGN KEY(subject_id,topic_id)
        REFERENCES topics(subject_id,id) ON DELETE RESTRICT,
    CONSTRAINT ck_question_versions_approval CHECK (
        (approval_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR (approval_status <> 'APPROVED' AND approved_at IS NULL)
    )
);
CREATE INDEX idx_question_versions_question_status ON question_versions(question_id,approval_status,version_no DESC);

CREATE TABLE question_options (
    id UUID PRIMARY KEY,
    question_version_id UUID NOT NULL REFERENCES question_versions(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK(position>=1),
    option_text TEXT NOT NULL CHECK(length(btrim(option_text)) > 0),
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_question_options_version_position UNIQUE(question_version_id,position),
    CONSTRAINT uq_question_options_version_id UNIQUE(question_version_id,id)
);
CREATE INDEX idx_question_options_version ON question_options(question_version_id,position);

-- APPROVED versions may not be edited; approval is permitted only with 2+ options / exactly 1 correct.
CREATE FUNCTION quizai_guard_question_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE n_all INT; n_correct INT; used BOOLEAN; owner_uuid UUID; owner_role TEXT; bank_uuid UUID;
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.approval_status='APPROVED' THEN
            RAISE EXCEPTION 'Create DRAFT, then approve after options exist' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    used:=EXISTS (SELECT 1 FROM quiz_questions WHERE question_version_id=OLD.id);
    -- V3 adds this relation; V2 can still be inspected/applied independently.
    IF to_regclass('practice_session_questions') IS NOT NULL THEN
        used:=used OR EXISTS (SELECT 1 FROM practice_session_questions WHERE question_version_id=OLD.id);
    END IF;
    IF OLD.approval_status='APPROVED' OR used THEN
        RAISE EXCEPTION 'Approved or used version is immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN
        RETURN OLD;
    END IF;
    IF NEW.approval_status='APPROVED' THEN
        SELECT q.owner_id,u.role,q.bank_id INTO owner_uuid,owner_role,bank_uuid
        FROM questions q JOIN users u ON u.id=q.owner_id WHERE q.id=NEW.question_id;
        IF owner_role='TEACHER' AND (
            NEW.subject_id IS NULL OR NEW.approved_by IS DISTINCT FROM owner_uuid
            OR NOT EXISTS (SELECT 1 FROM question_banks WHERE id=bank_uuid AND owner_teacher_id=owner_uuid)
        ) THEN
            RAISE EXCEPTION 'Teacher approval requires subject, owner approval and own primary bank' USING ERRCODE='23514';
        END IF;
        SELECT count(*), count(*) FILTER (WHERE is_correct) INTO n_all,n_correct
        FROM question_options WHERE question_version_id=NEW.id;
        IF n_all<2 OR n_correct<>1 THEN
            RAISE EXCEPTION 'Approved MCQ must have >=2 options and exactly 1 correct' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
-- Trigger installed after quiz_questions creation to avoid referencing a missing table.

CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    quiz_type TEXT NOT NULL CHECK(quiz_type IN ('PRACTICE','TEACHER_QUIZ')),
    title TEXT NOT NULL CHECK(length(btrim(title))>0),
    status TEXT NOT NULL CHECK(status IN ('DRAFT','READY','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMPTZ
);
CREATE INDEX idx_quizzes_owner_type ON quizzes(owner_id,quiz_type,status);

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
    question_version_id UUID NOT NULL REFERENCES question_versions(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK(position>=1),
    CONSTRAINT uq_quiz_questions_position UNIQUE(quiz_id,position)
);
CREATE INDEX idx_quiz_questions_version ON quiz_questions(question_version_id);

-- Serialize first use against edits/approval and option changes on the same version.
CREATE FUNCTION quizai_lock_quiz_question_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM question_versions WHERE id=NEW.question_version_id FOR UPDATE;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_quiz_question_version_lock BEFORE INSERT OR UPDATE ON quiz_questions
FOR EACH ROW EXECUTE FUNCTION quizai_lock_quiz_question_version();

CREATE TRIGGER trg_question_version_immutable BEFORE INSERT OR UPDATE OR DELETE ON question_versions
FOR EACH ROW EXECUTE FUNCTION quizai_guard_question_version();

CREATE FUNCTION quizai_guard_question_option() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_id UUID;
BEGIN
    v_id:=CASE WHEN TG_OP='DELETE' THEN OLD.question_version_id ELSE NEW.question_version_id END;
    IF EXISTS (SELECT 1 FROM question_versions WHERE id=v_id AND approval_status='APPROVED') THEN
        RAISE EXCEPTION 'Approved question options are immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND NEW.question_version_id<>OLD.question_version_id THEN
        RAISE EXCEPTION 'Option cannot switch question version' USING ERRCODE='23514';
    END IF;
    RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER trg_question_option_immutable BEFORE INSERT OR UPDATE OR DELETE ON question_options
FOR EACH ROW EXECUTE FUNCTION quizai_guard_question_option();

-- Additional cross-table ownership / APPROVE authorization checks require server-side transactions.
