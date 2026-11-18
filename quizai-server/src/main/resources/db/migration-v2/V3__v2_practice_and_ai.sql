-- V2-C: practice attempts and student-private AI conversations.
CREATE TABLE practice_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    quiz_id uuid NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
    status text NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','COMPLETED')),
    started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at timestamptz,
    correct_count integer,
    total_questions integer,
    CONSTRAINT practice_final_shape_ck CHECK (
        (status = 'IN_PROGRESS' AND finished_at IS NULL AND correct_count IS NULL AND total_questions IS NULL)
        OR (status = 'COMPLETED' AND finished_at IS NOT NULL AND finished_at >= started_at
            AND total_questions IS NOT NULL AND correct_count IS NOT NULL
            AND total_questions > 0 AND correct_count BETWEEN 0 AND total_questions)
    ),
    CONSTRAINT practice_owner_identity_uq UNIQUE (id, student_id)
);
CREATE INDEX practice_completed_student_idx ON practice_sessions(student_id, finished_at DESC)
    WHERE status = 'COMPLETED';

CREATE TABLE practice_session_questions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_session_id uuid NOT NULL REFERENCES practice_sessions(id) ON DELETE RESTRICT,
    question_version_id uuid NOT NULL REFERENCES question_versions(id) ON DELETE RESTRICT,
    position integer NOT NULL CHECK (position >= 1),
    CONSTRAINT practice_question_pos_uq UNIQUE (practice_session_id, position),
    CONSTRAINT practice_question_unique_version_uq UNIQUE (practice_session_id, question_version_id),
    CONSTRAINT practice_question_version_identity_uq UNIQUE (id, question_version_id)
);

CREATE TABLE practice_answers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_question_id uuid NOT NULL UNIQUE REFERENCES practice_session_questions(id) ON DELETE RESTRICT,
    selected_option_id uuid REFERENCES question_options(id) ON DELETE RESTRICT,
    is_correct boolean,
    answered_at timestamptz,
    CONSTRAINT practice_answer_completeness_ck CHECK (
        (selected_option_id IS NULL AND is_correct IS NULL AND answered_at IS NULL)
        OR (selected_option_id IS NOT NULL AND is_correct IS NOT NULL AND answered_at IS NOT NULL)
    )
);

CREATE TABLE ai_conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_student_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    conversation_type text NOT NULL CHECK (conversation_type IN ('PRACTICE_SESSION','PDF_TUTOR')),
    practice_session_id uuid,
    pdf_document_id uuid REFERENCES documents(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT conversation_practice_owner_fk FOREIGN KEY (practice_session_id, owner_student_id)
        REFERENCES practice_sessions(id, student_id) ON DELETE RESTRICT,
    CONSTRAINT conversation_context_ck CHECK (
        (conversation_type = 'PRACTICE_SESSION' AND practice_session_id IS NOT NULL AND pdf_document_id IS NULL)
        OR (conversation_type = 'PDF_TUTOR' AND practice_session_id IS NULL AND pdf_document_id IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ai_one_chat_per_practice_uq ON ai_conversations(practice_session_id)
    WHERE practice_session_id IS NOT NULL;
CREATE INDEX ai_conversations_student_idx ON ai_conversations(owner_student_id, created_at DESC);

CREATE TABLE ai_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES ai_conversations(id) ON DELETE RESTRICT,
    role text NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content text NOT NULL CHECK (length(content) > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    practice_session_question_id uuid REFERENCES practice_session_questions(id) ON DELETE RESTRICT
);
CREATE INDEX ai_messages_conversation_idx ON ai_messages(conversation_id, created_at, id);
