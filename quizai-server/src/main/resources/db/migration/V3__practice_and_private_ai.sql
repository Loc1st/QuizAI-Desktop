-- TASK-003 P2-C: five tables. Practice first, AI conversations second.
CREATE TABLE practice_sessions (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'IN_PROGRESS' CHECK(status IN ('IN_PROGRESS','COMPLETED','ABANDONED')),
    correct_count INTEGER CHECK(correct_count IS NULL OR correct_count>=0),
    total_questions INTEGER CHECK(total_questions IS NULL OR total_questions>0),
    CONSTRAINT ck_practice_session_completion CHECK (
      (status='COMPLETED' AND finished_at IS NOT NULL AND total_questions IS NOT NULL AND correct_count IS NOT NULL AND correct_count<=total_questions)
      OR (status<>'COMPLETED' AND finished_at IS NULL AND correct_count IS NULL AND total_questions IS NULL)
    ),
    CONSTRAINT ck_practice_session_finished_after_start CHECK(finished_at IS NULL OR finished_at>=started_at)
);
CREATE INDEX idx_practice_sessions_aggregate ON practice_sessions(student_id,status,finished_at DESC);
CREATE INDEX idx_practice_sessions_quiz ON practice_sessions(quiz_id, started_at DESC);

CREATE FUNCTION quizai_guard_practice_session() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'IN_PROGRESS' THEN
            RAISE EXCEPTION 'Practice session must start in progress' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status<>'IN_PROGRESS' THEN
        RAISE EXCEPTION 'Finished practice session and results are immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN
        RETURN OLD;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.student_id IS DISTINCT FROM OLD.student_id
       OR NEW.quiz_id IS DISTINCT FROM OLD.quiz_id OR NEW.started_at IS DISTINCT FROM OLD.started_at THEN
        RAISE EXCEPTION 'Practice session identity and source are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_practice_session_state BEFORE INSERT OR UPDATE OR DELETE ON practice_sessions
FOR EACH ROW EXECUTE FUNCTION quizai_guard_practice_session();

CREATE TABLE practice_session_questions (
    id UUID PRIMARY KEY,
    practice_session_id UUID NOT NULL REFERENCES practice_sessions(id) ON DELETE RESTRICT,
    question_version_id UUID NOT NULL REFERENCES question_versions(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK(position>=1),
    CONSTRAINT uq_practice_session_question_position UNIQUE(practice_session_id,position),
    CONSTRAINT uq_practice_session_question_version UNIQUE(id,question_version_id)
);
CREATE INDEX idx_practice_session_questions_order ON practice_session_questions(practice_session_id,position);

-- Initialize the complete list atomically with the session in the start transaction.
-- Existing rows cannot change; no additional lifecycle flag is introduced.
CREATE FUNCTION quizai_guard_practice_question_list() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE session_uuid UUID; session_status TEXT;
BEGIN
    IF TG_OP<>'INSERT' THEN
        RAISE EXCEPTION 'Practice question list is immutable' USING ERRCODE='23514';
    END IF;
    session_uuid:=NEW.practice_session_id;
    SELECT status INTO session_status FROM practice_sessions WHERE id=session_uuid FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Practice session does not exist' USING ERRCODE='23503';
    END IF;
    IF session_status<>'IN_PROGRESS' THEN
        RAISE EXCEPTION 'Finished practice question list cannot grow' USING ERRCODE='23514';
    END IF;
    PERFORM 1 FROM question_versions WHERE id=NEW.question_version_id FOR UPDATE;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_practice_question_list BEFORE INSERT OR UPDATE OR DELETE ON practice_session_questions
FOR EACH ROW EXECUTE FUNCTION quizai_guard_practice_question_list();

CREATE TABLE practice_answers (
    id UUID PRIMARY KEY,
    session_question_id UUID NOT NULL REFERENCES practice_session_questions(id) ON DELETE RESTRICT,
    selected_option_id UUID REFERENCES question_options(id) ON DELETE RESTRICT,
    answered_at TIMESTAMPTZ,
    is_correct BOOLEAN,
    CONSTRAINT uq_practice_answers_question UNIQUE(session_question_id),
    CONSTRAINT ck_practice_answer_time CHECK(selected_option_id IS NULL OR answered_at IS NOT NULL)
);
-- Match option to the actual question version in the practice session; DBX-08.
CREATE FUNCTION quizai_check_practice_option() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.selected_option_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM practice_session_questions sq
       JOIN question_options o ON o.question_version_id=sq.question_version_id
       WHERE sq.id=NEW.session_question_id AND o.id=NEW.selected_option_id
    ) THEN
        RAISE EXCEPTION 'Practice option belongs to another question version' USING ERRCODE='23503';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_practice_answer_option BEFORE INSERT OR UPDATE ON practice_answers
FOR EACH ROW EXECUTE FUNCTION quizai_check_practice_option();

-- Answer writes and finish both lock the session, so finalized answers cannot change.
CREATE FUNCTION quizai_guard_practice_answer_state() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE question_uuid UUID; session_uuid UUID; session_status TEXT;
BEGIN
    IF TG_OP='UPDATE' AND (NEW.id IS DISTINCT FROM OLD.id
       OR NEW.session_question_id IS DISTINCT FROM OLD.session_question_id) THEN
        RAISE EXCEPTION 'Practice answer identity and question are immutable' USING ERRCODE='23514';
    END IF;
    question_uuid:=CASE WHEN TG_OP='DELETE' THEN OLD.session_question_id ELSE NEW.session_question_id END;
    SELECT practice_session_id INTO session_uuid FROM practice_session_questions WHERE id=question_uuid;
    SELECT status INTO session_status FROM practice_sessions WHERE id=session_uuid FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Practice answer session does not exist' USING ERRCODE='23503';
    END IF;
    IF session_status<>'IN_PROGRESS' THEN
        RAISE EXCEPTION 'Finished practice answers are immutable' USING ERRCODE='23514';
    END IF;
    RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER trg_practice_answer_state BEFORE INSERT OR UPDATE OR DELETE ON practice_answers
FOR EACH ROW EXECUTE FUNCTION quizai_guard_practice_answer_state();

CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    owner_student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    conversation_type TEXT NOT NULL CHECK(conversation_type IN ('PRACTICE_SESSION','PDF_TUTOR')),
    practice_session_id UUID REFERENCES practice_sessions(id) ON DELETE RESTRICT,
    source_pdf_document_id UUID REFERENCES documents(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ai_conversation_context CHECK (
      (conversation_type='PRACTICE_SESSION' AND practice_session_id IS NOT NULL AND source_pdf_document_id IS NULL)
      OR (conversation_type='PDF_TUTOR' AND practice_session_id IS NULL AND source_pdf_document_id IS NOT NULL)
    ),
    CONSTRAINT uq_ai_conversations_practice UNIQUE(practice_session_id)
);
CREATE INDEX idx_ai_conversations_owner ON ai_conversations(owner_student_id,created_at DESC);

CREATE FUNCTION quizai_check_conversation_owner() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.conversation_type='PRACTICE_SESSION' AND NOT EXISTS (
        SELECT 1 FROM practice_sessions WHERE id=NEW.practice_session_id AND student_id=NEW.owner_student_id
    ) THEN
        RAISE EXCEPTION 'Practice conversation owner does not match session owner' USING ERRCODE='23514';
    END IF;
    IF NEW.conversation_type='PDF_TUTOR' AND NOT EXISTS (
        SELECT 1 FROM documents WHERE id=NEW.source_pdf_document_id
          AND owner_id=NEW.owner_student_id AND input_type='PDF' AND deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'PDF conversation requires private active PDF owned by student' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_ai_conversation_owner BEFORE INSERT OR UPDATE ON ai_conversations
FOR EACH ROW EXECUTE FUNCTION quizai_check_conversation_owner();

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE RESTRICT,
    role TEXT NOT NULL CHECK(role IN ('USER','ASSISTANT','SYSTEM')),
    content TEXT NOT NULL CHECK(length(btrim(content))>0),
    practice_session_question_id UUID REFERENCES practice_session_questions(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_messages_conversation_history ON ai_messages(conversation_id,created_at,id);
CREATE FUNCTION quizai_check_ai_message_context() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.practice_session_question_id IS NOT NULL AND NOT EXISTS (
      SELECT 1 FROM ai_conversations c JOIN practice_session_questions q
      ON q.practice_session_id=c.practice_session_id
      WHERE c.id=NEW.conversation_id AND c.conversation_type='PRACTICE_SESSION'
        AND q.id=NEW.practice_session_question_id
    ) THEN
       RAISE EXCEPTION 'AI question context is not in owning practice session' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_ai_message_context BEFORE INSERT ON ai_messages
FOR EACH ROW EXECUTE FUNCTION quizai_check_ai_message_context();
