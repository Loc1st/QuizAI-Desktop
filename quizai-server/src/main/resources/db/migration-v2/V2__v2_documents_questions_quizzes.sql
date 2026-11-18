-- V2-B: source documents, versioned MCQ, reusable quizzes.
CREATE TABLE documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    input_type text NOT NULL CHECK (input_type IN ('PDF','DOCX','TXT','PASTED_TEXT')),
    storage_key text,
    extracted_text_storage_key text,
    file_name text,
    mime_type text,
    size_bytes bigint,
    extraction_status text NOT NULL DEFAULT 'PENDING'
        CHECK (extraction_status IN ('PENDING','READY','FAILED')),
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT documents_size_ck CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT documents_file_storage_ck CHECK (input_type = 'PASTED_TEXT' OR storage_key IS NOT NULL),
    CONSTRAINT documents_ready_storage_ck CHECK (extraction_status <> 'READY' OR
        (extracted_text_storage_key IS NOT NULL AND length(btrim(extracted_text_storage_key)) > 0))
);
CREATE INDEX documents_owner_live_idx ON documents(owner_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE question_banks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_teacher_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name text NOT NULL,
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT bank_name_ck CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    CONSTRAINT bank_owner_identity_uq UNIQUE (id, owner_teacher_id)
);

CREATE TABLE questions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    bank_id uuid,
    source_document_id uuid REFERENCES documents(id) ON DELETE RESTRICT,
    origin text NOT NULL CHECK (origin IN ('AI','MANUAL')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT question_bank_same_owner_fk FOREIGN KEY (bank_id, owner_id)
        REFERENCES question_banks(id, owner_teacher_id) ON DELETE RESTRICT,
    CONSTRAINT question_owner_identity_uq UNIQUE (id, owner_id)
);
CREATE INDEX questions_owner_idx ON questions(owner_id, created_at DESC);
CREATE INDEX questions_bank_idx ON questions(bank_id);

CREATE TABLE question_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    version_no integer NOT NULL CHECK (version_no >= 1),
    stem text NOT NULL CHECK (length(btrim(stem)) > 0),
    subject_id uuid REFERENCES subjects(id) ON DELETE RESTRICT,
    topic_id uuid,
    difficulty text NOT NULL DEFAULT 'MEDIUM' CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    approval_status text NOT NULL DEFAULT 'DRAFT'
        CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED')),
    approved_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    approved_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT version_number_uq UNIQUE (question_id, version_no),
    CONSTRAINT version_topic_subject_fk FOREIGN KEY (subject_id, topic_id)
        REFERENCES topics(subject_id, id) ON DELETE RESTRICT,
    CONSTRAINT version_topic_needs_subject_ck CHECK (topic_id IS NULL OR subject_id IS NOT NULL),
    CONSTRAINT version_approval_metadata_ck CHECK (
        (approval_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR (approval_status <> 'APPROVED' AND approved_by IS NULL AND approved_at IS NULL)
    )
);
CREATE INDEX question_versions_question_idx ON question_versions(question_id, version_no DESC);

CREATE TABLE question_options (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_version_id uuid NOT NULL REFERENCES question_versions(id) ON DELETE RESTRICT,
    position integer NOT NULL CHECK (position >= 1),
    option_text text NOT NULL CHECK (length(btrim(option_text)) > 0),
    is_correct boolean NOT NULL DEFAULT false,
    CONSTRAINT question_options_pos_uq UNIQUE (question_version_id, position),
    CONSTRAINT question_options_version_identity_uq UNIQUE (question_version_id, id)
);
CREATE UNIQUE INDEX question_options_single_correct_uq ON question_options(question_version_id)
    WHERE is_correct = true;

CREATE TABLE quizzes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    kind text NOT NULL CHECK (kind IN ('PRACTICE','TEACHER_QUIZ')),
    title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT quizzes_owner_identity_uq UNIQUE (id, owner_id)
);
CREATE INDEX quizzes_owner_idx ON quizzes(owner_id, created_at DESC);

CREATE TABLE quiz_questions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id uuid NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
    question_version_id uuid NOT NULL REFERENCES question_versions(id) ON DELETE RESTRICT,
    position integer NOT NULL CHECK (position >= 1),
    CONSTRAINT quiz_questions_position_uq UNIQUE (quiz_id, position),
    CONSTRAINT quiz_questions_version_uq UNIQUE (quiz_id, question_version_id)
);
