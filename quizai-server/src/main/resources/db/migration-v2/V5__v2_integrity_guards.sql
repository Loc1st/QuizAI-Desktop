-- CR-001 draft: no new tables. No database execution in offline tests.
-- Deliberate MVP trade-off: serialize V2 mutations before row locks.
CREATE FUNCTION quizai_v2_serialize() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('transaction_isolation')<>'read committed' THEN
        RAISE EXCEPTION 'V2 mutations require READ COMMITTED' USING ERRCODE='25000'; END IF;
    PERFORM pg_advisory_xact_lock(81722024001);
    RETURN NULL;
END;
$$;
CREATE FUNCTION quizai_v2_lock_assignment(p_assignment uuid) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('transaction_isolation')<>'read committed' THEN
        RAISE EXCEPTION 'V2 mutations require READ COMMITTED' USING ERRCODE='25000'; END IF;
    PERFORM pg_advisory_xact_lock(81722024001);
    PERFORM 1 FROM assignments WHERE id=p_assignment FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'assignment unavailable' USING ERRCODE='23514'; END IF;
END;
$$;
CREATE FUNCTION quizai_v2_snapshot_valid(d jsonb) RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE item jsonb; seen text[]:=ARRAY[]::text[]; key text; answer_key text;
BEGIN
    IF jsonb_typeof(d) IS DISTINCT FROM 'object'
       OR jsonb_typeof(d->'schema_version') IS DISTINCT FROM 'number'
       OR d->'schema_version' IS DISTINCT FROM '1'::jsonb
       OR jsonb_typeof(d->'stem') IS DISTINCT FROM 'string' OR length(btrim(d->>'stem'))=0
       OR jsonb_typeof(d->'options') IS DISTINCT FROM 'array'
       OR jsonb_typeof(d->'correct_option_key') IS DISTINCT FROM 'string' THEN RETURN false; END IF;
    IF EXISTS(SELECT 1 FROM jsonb_object_keys(d) AS k WHERE k NOT IN
        ('schema_version','stem','options','correct_option_key')) THEN RETURN false; END IF;
    IF jsonb_array_length(d->'options')<2 THEN RETURN false; END IF;
    answer_key:=d->>'correct_option_key';
    FOR item IN SELECT value FROM jsonb_array_elements(d->'options') LOOP
        IF jsonb_typeof(item) IS DISTINCT FROM 'object' OR jsonb_typeof(item->'key') IS DISTINCT FROM 'string'
            OR jsonb_typeof(item->'text') IS DISTINCT FROM 'string' THEN RETURN false; END IF;
        IF EXISTS(SELECT 1 FROM jsonb_object_keys(item) AS k WHERE k NOT IN ('key','text')) THEN RETURN false; END IF;
        key:=item->>'key';
        IF length(btrim(key))=0 OR length(btrim(item->>'text'))=0 OR key=ANY(seen) THEN RETURN false; END IF;
        seen:=array_append(seen,key);
    END LOOP;
    RETURN coalesce(answer_key = ANY(seen), false);
EXCEPTION WHEN data_exception THEN RETURN false;
END;
$$;
ALTER TABLE assignment_questions ADD CONSTRAINT assignment_snapshot_valid_ck
    CHECK (quizai_v2_snapshot_valid(snapshot_json) IS TRUE);
CREATE FUNCTION quizai_v2_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR to_jsonb(NEW)->'created_at' IS DISTINCT FROM to_jsonb(OLD)->'created_at' THEN
        RAISE EXCEPTION 'identity and creation time immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_parent_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE uid uuid; r text;
BEGIN
    IF TG_TABLE_NAME='classes' THEN
        uid:=NEW.teacher_id;
        IF TG_OP='UPDATE' AND NEW.teacher_id IS DISTINCT FROM OLD.teacher_id THEN
            RAISE EXCEPTION 'class owner immutable' USING ERRCODE='23514'; END IF;
    ELSIF TG_TABLE_NAME='question_banks' THEN
        uid:=NEW.owner_teacher_id;
        IF TG_OP='UPDATE' AND NEW.owner_teacher_id IS DISTINCT FROM OLD.owner_teacher_id THEN
            RAISE EXCEPTION 'bank owner immutable' USING ERRCODE='23514'; END IF;
    ELSIF TG_TABLE_NAME='questions' THEN
        IF TG_OP='UPDATE' AND (NEW.owner_id,NEW.bank_id,NEW.source_document_id,NEW.origin)
            IS DISTINCT FROM (OLD.owner_id,OLD.bank_id,OLD.source_document_id,OLD.origin) THEN
            RAISE EXCEPTION 'question owner and provenance immutable' USING ERRCODE='23514'; END IF;
        IF NEW.source_document_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM documents
            WHERE id=NEW.source_document_id AND owner_id=NEW.owner_id AND deleted_at IS NULL) THEN
            RAISE EXCEPTION 'source document not owned' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    ELSIF TG_TABLE_NAME='documents' THEN
        IF TG_OP='UPDATE' AND (NEW.owner_id,NEW.input_type) IS DISTINCT FROM (OLD.owner_id,OLD.input_type) THEN
            RAISE EXCEPTION 'document owner and type immutable' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    ELSE
        SELECT role INTO r FROM users WHERE id=NEW.owner_id;
        IF (NEW.kind='TEACHER_QUIZ' AND r IS DISTINCT FROM 'TEACHER') OR (NEW.kind='PRACTICE' AND r IS DISTINCT FROM 'STUDENT') THEN
            RAISE EXCEPTION 'quiz kind owner role mismatch' USING ERRCODE='23514'; END IF;
        IF TG_OP='UPDATE' AND (NEW.owner_id,NEW.kind) IS DISTINCT FROM (OLD.owner_id,OLD.kind) THEN
            RAISE EXCEPTION 'quiz owner and kind immutable' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    SELECT role INTO r FROM users WHERE id=uid;
    IF r IS DISTINCT FROM 'TEACHER' THEN RAISE EXCEPTION 'teacher owner required' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_membership_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'membership history immutable' USING ERRCODE='23514'; END IF;
    IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.student_id AND role='STUDENT') THEN
        RAISE EXCEPTION 'student required' USING ERRCODE='23514'; END IF;
    IF TG_OP='INSERT' THEN
        NEW.joined_at:=clock_timestamp();
        IF NEW.left_at IS NOT NULL THEN RAISE EXCEPTION 'new episode must be active' USING ERRCODE='23514'; END IF;
    ELSE
        IF (NEW.class_id,NEW.student_id,NEW.joined_at) IS DISTINCT FROM (OLD.class_id,OLD.student_id,OLD.joined_at)
            OR OLD.left_at IS NOT NULL OR NEW.left_at IS NULL THEN
            RAISE EXCEPTION 'episode may only leave once' USING ERRCODE='23514'; END IF;
        NEW.left_at:=greatest(clock_timestamp(),OLD.joined_at);
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_version_used(p_version uuid) RETURNS boolean LANGUAGE sql VOLATILE AS $$
    SELECT EXISTS(SELECT 1 FROM quiz_questions WHERE question_version_id=p_version)
        OR EXISTS(SELECT 1 FROM practice_session_questions WHERE question_version_id=p_version)
        OR EXISTS(SELECT 1 FROM assignment_questions WHERE source_question_version_id=p_version);
$$;
CREATE FUNCTION quizai_v2_version_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner_id uuid; n integer; c integer;
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.approval_status='APPROVED' OR quizai_v2_version_used(OLD.id) THEN
            RAISE EXCEPTION 'used version immutable' USING ERRCODE='23514'; END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.approval_status='APPROVED' OR (NEW.question_id,NEW.version_no) IS DISTINCT FROM (OLD.question_id,OLD.version_no) THEN
            RAISE EXCEPTION 'approved version and identity immutable' USING ERRCODE='23514'; END IF;
        IF quizai_v2_version_used(OLD.id) AND (NEW.stem,NEW.subject_id,NEW.topic_id,NEW.difficulty)
            IS DISTINCT FROM (OLD.stem,OLD.subject_id,OLD.topic_id,OLD.difficulty) THEN
            RAISE EXCEPTION 'used version content immutable' USING ERRCODE='23514'; END IF;
    ELSIF NEW.approval_status<>'DRAFT' THEN RAISE EXCEPTION 'create draft before review' USING ERRCODE='23514'; END IF;
    IF NEW.approval_status='APPROVED' THEN
        SELECT q.owner_id INTO owner_id FROM questions q WHERE q.id=NEW.question_id;
        IF NEW.approved_by IS DISTINCT FROM owner_id OR NOT EXISTS(SELECT 1 FROM users WHERE id=owner_id AND role='TEACHER') THEN
            RAISE EXCEPTION 'only owner teacher may approve' USING ERRCODE='23514'; END IF;
        SELECT count(*),count(*) FILTER(WHERE is_correct) INTO n,c FROM question_options WHERE question_version_id=NEW.id;
        IF n<2 OR c<>1 THEN RAISE EXCEPTION 'invalid MCQ approval' USING ERRCODE='23514'; END IF;
        NEW.approved_at:=clock_timestamp();
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_options_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v uuid; s text;
BEGIN
    v:=CASE WHEN TG_OP='DELETE' THEN OLD.question_version_id ELSE NEW.question_version_id END;
    SELECT approval_status INTO s FROM question_versions WHERE id=v FOR UPDATE;
    IF s='APPROVED' OR quizai_v2_version_used(v) THEN RAISE EXCEPTION 'approved or used options immutable' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND NEW.question_version_id IS DISTINCT FROM OLD.question_version_id THEN
        RAISE EXCEPTION 'option cannot move between versions' USING ERRCODE='23514'; END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_quiz_question_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner_id uuid; version_owner uuid; n integer; c integer;
BEGIN
    -- Editing a quiz list cannot alter the separately frozen practice/assignment lists.
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    SELECT q.owner_id INTO version_owner FROM question_versions v JOIN questions q ON q.id=v.question_id WHERE v.id=NEW.question_version_id FOR UPDATE OF v;
    SELECT q.owner_id INTO owner_id FROM quizzes q WHERE q.id=NEW.quiz_id FOR UPDATE;
    IF owner_id IS DISTINCT FROM version_owner THEN RAISE EXCEPTION 'quiz owner mismatch' USING ERRCODE='23514'; END IF;
    SELECT count(*),count(*) FILTER(WHERE is_correct) INTO n,c FROM question_options WHERE question_version_id=NEW.question_version_id;
    IF n<2 OR c<>1 THEN RAISE EXCEPTION 'quiz requires usable MCQ' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_practice_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE qowner uuid; qkind text; n integer; c integer;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'practice history cannot be deleted' USING ERRCODE='23514'; END IF;
    IF TG_OP='INSERT' THEN
        SELECT owner_id,kind INTO qowner,qkind FROM quizzes WHERE id=NEW.quiz_id;
        IF qowner IS DISTINCT FROM NEW.student_id OR qkind IS DISTINCT FROM 'PRACTICE' OR NEW.status<>'IN_PROGRESS' THEN
            RAISE EXCEPTION 'private student practice required' USING ERRCODE='23514'; END IF;
        NEW.started_at:=clock_timestamp();
    ELSE
        IF OLD.status='COMPLETED' OR (NEW.student_id,NEW.quiz_id,NEW.started_at) IS DISTINCT FROM (OLD.student_id,OLD.quiz_id,OLD.started_at) THEN
            RAISE EXCEPTION 'practice identity or completed history immutable' USING ERRCODE='23514'; END IF;
        IF NEW.status='COMPLETED' THEN
            SELECT count(*),count(*) FILTER(WHERE a.is_correct IS TRUE) INTO n,c FROM practice_session_questions q
                LEFT JOIN practice_answers a ON a.session_question_id=q.id WHERE q.practice_session_id=NEW.id;
            IF NEW.total_questions IS NULL OR NEW.correct_count IS NULL OR NEW.total_questions<>n OR NEW.correct_count<>c OR n=0 THEN
                RAISE EXCEPTION 'completion requires actual nonnull counts' USING ERRCODE='23514'; END IF;
            NEW.finished_at:=clock_timestamp();
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_practice_question_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE student uuid; owner_id uuid; s text;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'practice question list immutable' USING ERRCODE='23514'; END IF;
    SELECT student_id,status INTO student,s FROM practice_sessions WHERE id=NEW.practice_session_id FOR UPDATE;
    SELECT q.owner_id INTO owner_id FROM question_versions v JOIN questions q ON q.id=v.question_id WHERE v.id=NEW.question_version_id FOR UPDATE OF v;
    IF s IS DISTINCT FROM 'IN_PROGRESS' OR student IS DISTINCT FROM owner_id OR NOT EXISTS (
        SELECT 1 FROM practice_sessions p JOIN quiz_questions q ON q.quiz_id=p.quiz_id
            WHERE p.id=NEW.practice_session_id AND q.question_version_id=NEW.question_version_id)
        OR EXISTS(SELECT 1 FROM practice_answers a JOIN practice_session_questions q ON q.id=a.session_question_id
            WHERE q.practice_session_id=NEW.practice_session_id) THEN
        RAISE EXCEPTION 'build private list from quiz before answering' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_practice_answer_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v uuid; s text; option_version uuid; correct boolean;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'clear active answer with NULL instead of deleting history' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND NEW.session_question_id IS DISTINCT FROM OLD.session_question_id THEN
        RAISE EXCEPTION 'practice answer identity immutable' USING ERRCODE='23514'; END IF;
    SELECT q.question_version_id,p.status INTO v,s FROM practice_session_questions q JOIN practice_sessions p ON p.id=q.practice_session_id
        WHERE q.id=NEW.session_question_id FOR UPDATE OF p;
    IF s IS DISTINCT FROM 'IN_PROGRESS' THEN RAISE EXCEPTION 'completed answers immutable' USING ERRCODE='23514'; END IF;
    IF NEW.selected_option_id IS NULL THEN NEW.is_correct:=NULL; NEW.answered_at:=NULL;
    ELSE
        SELECT question_version_id,is_correct INTO option_version,correct FROM question_options WHERE id=NEW.selected_option_id;
        IF option_version IS DISTINCT FROM v THEN RAISE EXCEPTION 'wrong question option' USING ERRCODE='23514'; END IF;
        NEW.is_correct:=correct; NEW.answered_at:=clock_timestamp();
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_conversation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.owner_student_id AND role='STUDENT') THEN
        RAISE EXCEPTION 'student conversation required' USING ERRCODE='23514'; END IF;
    IF NEW.conversation_type='PDF_TUTOR' AND NOT EXISTS(SELECT 1 FROM documents WHERE id=NEW.pdf_document_id
        AND owner_id=NEW.owner_student_id AND input_type='PDF' AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'own live PDF required' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND (NEW.owner_student_id,NEW.conversation_type,NEW.practice_session_id,NEW.pdf_document_id)
        IS DISTINCT FROM (OLD.owner_student_id,OLD.conversation_type,OLD.practice_session_id,OLD.pdf_document_id) THEN
        RAISE EXCEPTION 'conversation context immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_ai_message_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE conv ai_conversations%ROWTYPE;
BEGIN
    SELECT * INTO conv FROM ai_conversations WHERE id=NEW.conversation_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'conversation missing' USING ERRCODE='23514'; END IF;
    IF NEW.practice_session_question_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM practice_session_questions
        WHERE id=NEW.practice_session_question_id AND practice_session_id=conv.practice_session_id
            AND conv.conversation_type='PRACTICE_SESSION') THEN
        RAISE EXCEPTION 'AI context mismatch' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM attempts a JOIN assignment_recipients r ON r.id=a.recipient_id
        WHERE r.student_id=conv.owner_student_id AND a.status='IN_PROGRESS') THEN
        RAISE EXCEPTION 'AI blocked during assigned exam; finalize expired exam first' USING ERRCODE='23514'; END IF;
    NEW.created_at:=clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_assignment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE score_eligible boolean; answer_eligible boolean; t timestamptz:=clock_timestamp();
BEGIN
    IF NOT EXISTS(SELECT 1 FROM classes c JOIN quizzes q ON q.id=NEW.quiz_id WHERE c.id=NEW.class_id
        AND c.teacher_id=NEW.teacher_id AND q.owner_id=NEW.teacher_id AND q.kind='TEACHER_QUIZ') THEN
        RAISE EXCEPTION 'teacher must own class and teacher quiz' USING ERRCODE='23514'; END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'DRAFT' OR NEW.score_first_available_at IS NOT NULL OR NEW.answer_first_available_at IS NOT NULL THEN
            RAISE EXCEPTION 'create draft with no release witnesses' USING ERRCODE='23514'; END IF;
    ELSE
        IF (NEW.class_id,NEW.teacher_id,NEW.quiz_id) IS DISTINCT FROM (OLD.class_id,OLD.teacher_id,OLD.quiz_id)
            AND (EXISTS(SELECT 1 FROM assignment_recipients WHERE assignment_id=OLD.id)
                OR EXISTS(SELECT 1 FROM assignment_questions WHERE assignment_id=OLD.id)) THEN
            RAISE EXCEPTION 'populated assignment parent immutable' USING ERRCODE='23514'; END IF;
        IF OLD.status='PUBLISHED' AND (NEW.status,NEW.class_id,NEW.teacher_id,NEW.quiz_id,NEW.opens_at,NEW.closes_at,NEW.duration_minutes,NEW.max_attempts)
            IS DISTINCT FROM (OLD.status,OLD.class_id,OLD.teacher_id,OLD.quiz_id,OLD.opens_at,OLD.closes_at,OLD.duration_minutes,OLD.max_attempts) THEN
            RAISE EXCEPTION 'published assignment core immutable' USING ERRCODE='23514'; END IF;
        IF NEW.status='PUBLISHED' AND OLD.status='DRAFT' AND (NOT EXISTS(SELECT 1 FROM assignment_questions WHERE assignment_id=NEW.id)
            OR NOT EXISTS(SELECT 1 FROM assignment_recipients WHERE assignment_id=NEW.id)) THEN
            RAISE EXCEPTION 'publish requires recipients and snapshots' USING ERRCODE='23514'; END IF;
        score_eligible:=OLD.score_first_available_at IS NOT NULL OR (OLD.status='PUBLISHED' AND OLD.score_release_at IS NOT NULL
            AND t>=OLD.score_release_at AND EXISTS(SELECT 1 FROM attempts WHERE assignment_id=OLD.id AND status IN ('SUBMITTED','TIMED_OUT')));
        answer_eligible:=OLD.answer_first_available_at IS NOT NULL OR (OLD.status='PUBLISHED' AND OLD.answer_release_at IS NOT NULL AND t>=OLD.answer_release_at
            AND ((OLD.answer_release_mode='AFTER_ASSIGNMENT_CLOSE' AND t>=OLD.closes_at AND EXISTS(SELECT 1 FROM assignment_recipients WHERE assignment_id=OLD.id))
                OR (OLD.answer_release_mode='AFTER_VALID_SUBMISSION' AND EXISTS(SELECT 1 FROM attempts WHERE assignment_id=OLD.id AND status IN ('SUBMITTED','TIMED_OUT')))));
        IF score_eligible AND NEW.score_release_at IS DISTINCT FROM OLD.score_release_at THEN
            RAISE EXCEPTION 'score release cannot be withdrawn' USING ERRCODE='23514'; END IF;
        IF answer_eligible AND (NEW.answer_release_at,NEW.answer_release_mode) IS DISTINCT FROM (OLD.answer_release_at,OLD.answer_release_mode) THEN
            RAISE EXCEPTION 'answer release cannot be withdrawn' USING ERRCODE='23514'; END IF;
        NEW.score_first_available_at:=coalesce(OLD.score_first_available_at,CASE WHEN score_eligible THEN t END);
        NEW.answer_first_available_at:=coalesce(OLD.answer_first_available_at,CASE WHEN answer_eligible THEN t END);
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_recipient_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'recipient history immutable' USING ERRCODE='23514'; END IF;
    IF NOT EXISTS(SELECT 1 FROM assignments a JOIN class_members m ON m.class_id=a.class_id WHERE a.id=NEW.assignment_id
        AND a.status='DRAFT' AND m.id=NEW.membership_id AND m.student_id=NEW.student_id AND m.left_at IS NULL) THEN
        RAISE EXCEPTION 'recipient must match original active episode before publication' USING ERRCODE='23514'; END IF;
    NEW.assigned_at:=clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_snapshot_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE aid uuid; s text; teacher uuid; v question_versions%ROWTYPE; owner_id uuid; texts jsonb; provided jsonb; correct_pos integer;
BEGIN
    aid:=CASE WHEN TG_OP='DELETE' THEN OLD.assignment_id ELSE NEW.assignment_id END;
    PERFORM quizai_v2_lock_assignment(aid);
    SELECT status,teacher_id INTO s,teacher FROM assignments WHERE id=aid;
    IF s IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'published snapshot immutable' USING ERRCODE='23514'; END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF TG_OP='UPDATE' AND NEW.assignment_id IS DISTINCT FROM OLD.assignment_id THEN
        RAISE EXCEPTION 'snapshot cannot change assignment' USING ERRCODE='23514'; END IF;
    IF quizai_v2_snapshot_valid(NEW.snapshot_json) IS NOT TRUE THEN RAISE EXCEPTION 'invalid snapshot JSON' USING ERRCODE='23514'; END IF;
    IF NEW.source_question_version_id IS NOT NULL THEN
        SELECT * INTO v FROM question_versions WHERE id=NEW.source_question_version_id;
        SELECT q.owner_id INTO owner_id FROM questions q WHERE q.id=v.question_id;
        IF owner_id IS DISTINCT FROM teacher OR v.approval_status IS DISTINCT FROM 'APPROVED' OR NEW.snapshot_json->>'stem' IS DISTINCT FROM v.stem THEN
            RAISE EXCEPTION 'snapshot source must be approved and owned' USING ERRCODE='23514'; END IF;
        SELECT jsonb_agg(option_text ORDER BY position) INTO texts FROM question_options WHERE question_version_id=v.id;
        SELECT jsonb_agg(value->'text' ORDER BY ordinality) INTO provided FROM jsonb_array_elements(NEW.snapshot_json->'options') WITH ORDINALITY;
        SELECT ord INTO correct_pos FROM (SELECT is_correct,row_number() OVER(ORDER BY position)::integer AS ord
            FROM question_options WHERE question_version_id=v.id) ranked WHERE is_correct;
        IF texts IS DISTINCT FROM provided OR correct_pos IS NULL OR
            NEW.snapshot_json->'options'->(correct_pos-1)->>'key' IS DISTINCT FROM NEW.snapshot_json->>'correct_option_key' THEN
            RAISE EXCEPTION 'snapshot must copy reviewed options and answer' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_attempt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a assignments%ROWTYPE; r assignment_recipients%ROWTYPE; m class_members%ROWTYPE; n integer; earned integer; possible integer; t timestamptz;
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'attempt history cannot be deleted' USING ERRCODE='23514'; END IF;
    PERFORM quizai_v2_lock_assignment(NEW.assignment_id);
    SELECT * INTO a FROM assignments WHERE id=NEW.assignment_id;
    SELECT * INTO r FROM assignment_recipients WHERE id=NEW.recipient_id FOR UPDATE;
    IF r.assignment_id IS DISTINCT FROM a.id THEN RAISE EXCEPTION 'recipient assignment mismatch' USING ERRCODE='23514'; END IF;
    t:=clock_timestamp();
    IF TG_OP='INSERT' THEN
        SELECT * INTO m FROM class_members WHERE id=r.membership_id FOR UPDATE;
        IF a.status IS DISTINCT FROM 'PUBLISHED' OR m.left_at IS NOT NULL OR m.class_id IS DISTINCT FROM a.class_id
            OR m.student_id IS DISTINCT FROM r.student_id OR t<a.opens_at OR t>=a.closes_at THEN
            RAISE EXCEPTION 'assignment not startable for original episode' USING ERRCODE='23514'; END IF;
        SELECT count(*) INTO n FROM attempts WHERE recipient_id=r.id;
        IF n>=a.max_attempts OR NEW.attempt_no<>n+1 OR NEW.status<>'IN_PROGRESS' THEN
            RAISE EXCEPTION 'max attempts or start state violated' USING ERRCODE='23514'; END IF;
        NEW.started_at:=t; NEW.deadline_at:=least(a.closes_at,t+a.duration_minutes*interval '1 minute');
    ELSE
        IF OLD.status IN ('SUBMITTED','TIMED_OUT') THEN RAISE EXCEPTION 'finalized attempt immutable' USING ERRCODE='23514'; END IF;
        IF (NEW.assignment_id,NEW.recipient_id,NEW.attempt_no,NEW.start_request_id,NEW.started_at,NEW.deadline_at)
            IS DISTINCT FROM (OLD.assignment_id,OLD.recipient_id,OLD.attempt_no,OLD.start_request_id,OLD.started_at,OLD.deadline_at)
            OR NEW.status NOT IN ('SUBMITTED','TIMED_OUT') THEN
            RAISE EXCEPTION 'only terminal transition permitted' USING ERRCODE='23514'; END IF;
        IF NEW.status='SUBMITTED' AND t>=OLD.deadline_at THEN RAISE EXCEPTION 'deadline passed; finalize timeout' USING ERRCODE='23514'; END IF;
        IF NEW.status='TIMED_OUT' AND t<OLD.deadline_at THEN RAISE EXCEPTION 'timeout before deadline' USING ERRCODE='23514'; END IF;
        SELECT count(*),count(*) FILTER(WHERE ans.selected_option_key=q.snapshot_json->>'correct_option_key') INTO possible,earned
            FROM assignment_questions q LEFT JOIN attempt_answers ans ON ans.assignment_question_id=q.id AND ans.attempt_id=OLD.id WHERE q.assignment_id=a.id;
        IF NEW.earned_points IS NULL OR NEW.possible_points IS NULL OR NEW.normalized_score IS NULL
            OR NEW.earned_points<>earned OR NEW.possible_points<>possible
            OR NEW.normalized_score IS DISTINCT FROM round(earned::numeric/nullif(possible,0),6) THEN
            RAISE EXCEPTION 'terminal scores must match ACKed answers' USING ERRCODE='23514'; END IF;
        NEW.submitted_at:=t;
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_attempt_answer_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a attempts%ROWTYPE; aid uuid; qaid uuid; opts jsonb; accepted_at timestamptz;
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'clear active option through autosave, never delete history' USING ERRCODE='23514'; END IF;
    SELECT assignment_id INTO aid FROM attempts WHERE id=NEW.attempt_id;
    PERFORM quizai_v2_lock_assignment(aid);
    SELECT * INTO a FROM attempts WHERE id=NEW.attempt_id FOR UPDATE;
    IF TG_OP='UPDATE' AND (NEW.attempt_id,NEW.assignment_question_id) IS DISTINCT FROM (OLD.attempt_id,OLD.assignment_question_id) THEN
        RAISE EXCEPTION 'saved answer identity immutable' USING ERRCODE='23514'; END IF;
    IF a.status IS DISTINCT FROM 'IN_PROGRESS' OR clock_timestamp()>=a.deadline_at THEN
        RAISE EXCEPTION 'answer after deadline or finalization' USING ERRCODE='23514'; END IF;
    SELECT assignment_id,snapshot_json->'options' INTO qaid,opts FROM assignment_questions WHERE id=NEW.assignment_question_id;
    IF qaid IS DISTINCT FROM a.assignment_id OR (NEW.selected_option_key IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(opts) o WHERE o->>'key'=NEW.selected_option_key)) THEN
        RAISE EXCEPTION 'answer assignment or option mismatch' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND NEW.answer_revision<=OLD.answer_revision THEN RAISE EXCEPTION 'stale revision' USING ERRCODE='23514'; END IF;
    accepted_at:=clock_timestamp();
    IF accepted_at>=a.deadline_at THEN RAISE EXCEPTION 'deadline crossed while validating answer' USING ERRCODE='23514'; END IF;
    NEW.server_saved_at:=accepted_at;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_start_attempt(p_student uuid,p_recipient uuid,p_request uuid) RETURNS attempts LANGUAGE plpgsql AS $$
DECLARE r assignment_recipients%ROWTYPE; result attempts%ROWTYPE; n integer;
BEGIN
    PERFORM pg_advisory_xact_lock(81722024001);
    SELECT * INTO r FROM assignment_recipients WHERE id=p_recipient;
    PERFORM quizai_v2_lock_assignment(r.assignment_id);
    IF r.student_id IS DISTINCT FROM p_student OR p_request IS NULL THEN RAISE EXCEPTION 'attempt owner or request invalid' USING ERRCODE='23514'; END IF;
    SELECT * INTO result FROM attempts WHERE recipient_id=p_recipient AND start_request_id=p_request;
    IF FOUND THEN RETURN result; END IF;
    SELECT count(*)+1 INTO n FROM attempts WHERE recipient_id=p_recipient;
    INSERT INTO attempts(assignment_id,recipient_id,attempt_no,start_request_id,deadline_at)
        VALUES(r.assignment_id,r.id,n,p_request,clock_timestamp()) RETURNING * INTO result;
    RETURN result;
END;
$$;
CREATE FUNCTION quizai_v2_save_answer(p_student uuid,p_attempt uuid,p_question uuid,p_option text,p_revision bigint)
RETURNS attempt_answers LANGUAGE plpgsql AS $$
DECLARE aid uuid; a attempts%ROWTYPE; saved attempt_answers%ROWTYPE;
BEGIN
    PERFORM pg_advisory_xact_lock(81722024001);
    SELECT assignment_id INTO aid FROM attempts WHERE id=p_attempt;
    PERFORM quizai_v2_lock_assignment(aid);
    SELECT * INTO a FROM attempts WHERE id=p_attempt FOR UPDATE;
    IF NOT EXISTS(SELECT 1 FROM assignment_recipients WHERE id=a.recipient_id AND student_id=p_student) OR p_revision IS NULL OR p_revision<0 THEN
        RAISE EXCEPTION 'answer owner or revision invalid' USING ERRCODE='23514'; END IF;
    SELECT * INTO saved FROM attempt_answers WHERE attempt_id=p_attempt AND assignment_question_id=p_question;
    IF FOUND AND p_revision = saved.answer_revision AND saved.selected_option_key IS NOT DISTINCT FROM p_option THEN RETURN saved; END IF;
    IF a.status IS DISTINCT FROM 'IN_PROGRESS' OR clock_timestamp()>=a.deadline_at THEN
        RAISE EXCEPTION 'cannot save after terminal or deadline' USING ERRCODE='23514'; END IF;
    IF saved.id IS NOT NULL THEN
        IF p_revision<=saved.answer_revision THEN RAISE EXCEPTION 'stale or conflicting revision' USING ERRCODE='23514'; END IF;
        UPDATE attempt_answers SET selected_option_key=p_option,answer_revision=p_revision WHERE id=saved.id RETURNING * INTO saved;
    ELSE
        INSERT INTO attempt_answers(attempt_id,assignment_question_id,selected_option_key,answer_revision)
            VALUES(p_attempt,p_question,p_option,p_revision) RETURNING * INTO saved;
    END IF;
    RETURN saved;
END;
$$;
CREATE FUNCTION quizai_v2_finalize_attempt(p_student uuid,p_attempt uuid,p_request uuid,p_digest text)
RETURNS attempts LANGUAGE plpgsql AS $$
DECLARE a attempts%ROWTYPE; aid uuid; earned integer; possible integer; terminal text;
BEGIN
    PERFORM pg_advisory_xact_lock(81722024001);
    SELECT assignment_id INTO aid FROM attempts WHERE id=p_attempt;
    PERFORM quizai_v2_lock_assignment(aid);
    SELECT * INTO a FROM attempts WHERE id=p_attempt FOR UPDATE;
    IF NOT EXISTS(SELECT 1 FROM assignment_recipients WHERE id=a.recipient_id AND student_id=p_student) THEN
        RAISE EXCEPTION 'attempt owner mismatch' USING ERRCODE='23514'; END IF;
    IF a.status='SUBMITTED' THEN
        IF a.submit_request_id IS NOT DISTINCT FROM p_request AND a.submitted_digest IS NOT DISTINCT FROM p_digest THEN RETURN a; END IF;
        RAISE EXCEPTION 'conflicting terminal submit' USING ERRCODE='23514';
    ELSIF a.status='TIMED_OUT' THEN RETURN a; END IF;
    terminal:=CASE WHEN clock_timestamp()>=a.deadline_at THEN 'TIMED_OUT' ELSE 'SUBMITTED' END;
    IF terminal='SUBMITTED' AND (p_request IS NULL OR p_digest IS NULL OR p_digest !~ '^[0-9a-f]{64}$') THEN
        RAISE EXCEPTION 'submit requires request and SHA256-shaped digest' USING ERRCODE='23514'; END IF;
    SELECT count(*),count(*) FILTER(WHERE ans.selected_option_key=q.snapshot_json->>'correct_option_key') INTO possible,earned
        FROM assignment_questions q LEFT JOIN attempt_answers ans ON ans.assignment_question_id=q.id AND ans.attempt_id=a.id WHERE q.assignment_id=a.assignment_id;
    IF clock_timestamp()>=a.deadline_at THEN terminal:='TIMED_OUT'; END IF;
    BEGIN
        UPDATE attempts SET status=terminal,submitted_at=clock_timestamp(),earned_points=earned,possible_points=possible,
            normalized_score=round(earned::numeric/nullif(possible,0),6),
            submit_request_id=CASE WHEN terminal='SUBMITTED' THEN p_request END,
            submitted_digest=CASE WHEN terminal='SUBMITTED' THEN p_digest END WHERE id=a.id RETURNING * INTO a;
    EXCEPTION WHEN check_violation THEN
        -- Deadline may cross between the last sample and the BEFORE ROW guard.
        -- Retry only as timeout, under the same locks, using the same ACKed answers.
        IF clock_timestamp()<a.deadline_at THEN RAISE; END IF;
        UPDATE attempts SET status='TIMED_OUT',submitted_at=clock_timestamp(),earned_points=earned,possible_points=possible,
            normalized_score=round(earned::numeric/nullif(possible,0),6),submit_request_id=NULL,submitted_digest=NULL
            WHERE id=p_attempt RETURNING * INTO a;
    END;
    RETURN a;
END;
$$;
-- p_teacher is the authenticated backend principal, never an untrusted request role.
-- NULL provenance remains the ERD's manual-authoring path; publish is owner review.
-- AI-generated input must retain its APPROVED source version through the generation pipeline.
CREATE FUNCTION quizai_v2_publish_assignment(p_teacher uuid,p_assignment uuid) RETURNS assignments LANGUAGE plpgsql AS $$
DECLARE a assignments%ROWTYPE;
BEGIN
    PERFORM quizai_v2_lock_assignment(p_assignment);
    SELECT * INTO a FROM assignments WHERE id=p_assignment FOR UPDATE;
    IF a.teacher_id IS DISTINCT FROM p_teacher OR NOT EXISTS(SELECT 1 FROM users WHERE id=p_teacher AND role='TEACHER') THEN
        RAISE EXCEPTION 'only assignment owner teacher may review and publish' USING ERRCODE='23514'; END IF;
    IF a.status='PUBLISHED' THEN RETURN a; END IF;
    UPDATE assignments SET status='PUBLISHED' WHERE id=a.id RETURNING * INTO a;
    RETURN a;
END;
$$;
CREATE FUNCTION quizai_v2_student_questions(p_student uuid,p_attempt uuid)
RETURNS TABLE(id uuid,"position" integer,stem text,options jsonb) LANGUAGE plpgsql AS $$
DECLARE aid uuid;
BEGIN
    SELECT a.assignment_id INTO aid FROM attempts a JOIN assignment_recipients r ON r.id=a.recipient_id WHERE a.id=p_attempt AND r.student_id=p_student;
    IF aid IS NULL THEN RAISE EXCEPTION 'attempt unavailable' USING ERRCODE='23514'; END IF;
    RETURN QUERY SELECT q.id,q.position,q.snapshot_json->>'stem',
        (SELECT jsonb_agg(jsonb_build_object('key',o.value->>'key','text',o.value->>'text') ORDER BY o.ordinality)
            FROM jsonb_array_elements(q.snapshot_json->'options') WITH ORDINALITY o(value,ordinality))
        FROM assignment_questions q WHERE q.assignment_id=aid ORDER BY q.position;
END;
$$;
CREATE FUNCTION quizai_v2_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'history is append-only' USING ERRCODE='23514'; END;
$$;
CREATE FUNCTION quizai_v2_user_role_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role THEN RAISE EXCEPTION 'role immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION quizai_v2_session_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.user_id,NEW.token_hash,NEW.expires_at) IS DISTINCT FROM (OLD.user_id,OLD.token_hash,OLD.expires_at)
        OR OLD.revoked_at IS NOT NULL THEN
        RAISE EXCEPTION 'session principal, token and revocation immutable' USING ERRCODE='23514'; END IF;
    IF NEW.revoked_at IS NOT NULL THEN NEW.revoked_at:=greatest(clock_timestamp(),OLD.created_at); END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER session_principal_guard BEFORE UPDATE ON auth_sessions FOR EACH ROW EXECUTE FUNCTION quizai_v2_session_guard();
CREATE TRIGGER guard_classes_parent BEFORE INSERT OR UPDATE ON classes FOR EACH ROW EXECUTE FUNCTION quizai_v2_parent_guard();
CREATE TRIGGER guard_question_banks_parent BEFORE INSERT OR UPDATE ON question_banks FOR EACH ROW EXECUTE FUNCTION quizai_v2_parent_guard();
CREATE TRIGGER guard_questions_parent BEFORE INSERT OR UPDATE ON questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_parent_guard();
CREATE TRIGGER guard_documents_parent BEFORE INSERT OR UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION quizai_v2_parent_guard();
CREATE TRIGGER guard_quizzes_parent BEFORE INSERT OR UPDATE ON quizzes FOR EACH ROW EXECUTE FUNCTION quizai_v2_parent_guard();
CREATE TRIGGER guard_class_members_membership BEFORE INSERT OR UPDATE OR DELETE ON class_members FOR EACH ROW EXECUTE FUNCTION quizai_v2_membership_guard();
CREATE TRIGGER guard_question_versions_version BEFORE INSERT OR UPDATE OR DELETE ON question_versions FOR EACH ROW EXECUTE FUNCTION quizai_v2_version_guard();
CREATE TRIGGER guard_question_options_options BEFORE INSERT OR UPDATE OR DELETE ON question_options FOR EACH ROW EXECUTE FUNCTION quizai_v2_options_guard();
CREATE TRIGGER guard_quiz_questions_quiz_question BEFORE INSERT OR UPDATE OR DELETE ON quiz_questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_quiz_question_guard();
CREATE TRIGGER guard_practice_sessions_practice BEFORE INSERT OR UPDATE OR DELETE ON practice_sessions FOR EACH ROW EXECUTE FUNCTION quizai_v2_practice_guard();
CREATE TRIGGER guard_practice_session_questions_practice_question BEFORE INSERT OR UPDATE OR DELETE ON practice_session_questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_practice_question_guard();
CREATE TRIGGER guard_practice_answers_practice_answer BEFORE INSERT OR UPDATE OR DELETE ON practice_answers FOR EACH ROW EXECUTE FUNCTION quizai_v2_practice_answer_guard();
CREATE TRIGGER guard_ai_conversations_conversation BEFORE INSERT OR UPDATE ON ai_conversations FOR EACH ROW EXECUTE FUNCTION quizai_v2_conversation_guard();
CREATE TRIGGER guard_ai_messages_ai_message BEFORE INSERT ON ai_messages FOR EACH ROW EXECUTE FUNCTION quizai_v2_ai_message_guard();
CREATE TRIGGER guard_ai_messages_append_only BEFORE UPDATE OR DELETE ON ai_messages FOR EACH ROW EXECUTE FUNCTION quizai_v2_append_only();
CREATE TRIGGER guard_ai_conversations_append_only BEFORE DELETE ON ai_conversations FOR EACH ROW EXECUTE FUNCTION quizai_v2_append_only();
CREATE TRIGGER guard_assignments_assignment BEFORE INSERT OR UPDATE ON assignments FOR EACH ROW EXECUTE FUNCTION quizai_v2_assignment_guard();
CREATE TRIGGER guard_assignments_append_only BEFORE DELETE ON assignments FOR EACH ROW EXECUTE FUNCTION quizai_v2_append_only();
CREATE TRIGGER guard_assignment_recipients_recipient BEFORE INSERT OR UPDATE OR DELETE ON assignment_recipients FOR EACH ROW EXECUTE FUNCTION quizai_v2_recipient_guard();
CREATE TRIGGER guard_assignment_questions_snapshot BEFORE INSERT OR UPDATE OR DELETE ON assignment_questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_snapshot_guard();
CREATE TRIGGER guard_attempts_attempt BEFORE INSERT OR UPDATE OR DELETE ON attempts FOR EACH ROW EXECUTE FUNCTION quizai_v2_attempt_guard();
CREATE TRIGGER guard_attempt_answers_attempt_answer BEFORE INSERT OR UPDATE OR DELETE ON attempt_answers FOR EACH ROW EXECUTE FUNCTION quizai_v2_attempt_answer_guard();
CREATE TRIGGER guard_audit_events_append_only BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION quizai_v2_append_only();
CREATE TRIGGER guard_users_user_role BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION quizai_v2_user_role_guard();
CREATE TRIGGER v2_serialize_users BEFORE INSERT OR UPDATE OR DELETE ON users FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_users BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_auth_sessions BEFORE INSERT OR UPDATE OR DELETE ON auth_sessions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_auth_sessions BEFORE UPDATE ON auth_sessions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_classes BEFORE INSERT OR UPDATE OR DELETE ON classes FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_classes BEFORE UPDATE ON classes FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_class_members BEFORE INSERT OR UPDATE OR DELETE ON class_members FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_class_members BEFORE UPDATE ON class_members FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_subjects BEFORE INSERT OR UPDATE OR DELETE ON subjects FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_subjects BEFORE UPDATE ON subjects FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_topics BEFORE INSERT OR UPDATE OR DELETE ON topics FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_topics BEFORE UPDATE ON topics FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_documents BEFORE INSERT OR UPDATE OR DELETE ON documents FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_documents BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_question_banks BEFORE INSERT OR UPDATE OR DELETE ON question_banks FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_question_banks BEFORE UPDATE ON question_banks FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_questions BEFORE INSERT OR UPDATE OR DELETE ON questions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_questions BEFORE UPDATE ON questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_question_versions BEFORE INSERT OR UPDATE OR DELETE ON question_versions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_question_versions BEFORE UPDATE ON question_versions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_question_options BEFORE INSERT OR UPDATE OR DELETE ON question_options FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_question_options BEFORE UPDATE ON question_options FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_quizzes BEFORE INSERT OR UPDATE OR DELETE ON quizzes FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_quizzes BEFORE UPDATE ON quizzes FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_quiz_questions BEFORE INSERT OR UPDATE OR DELETE ON quiz_questions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_quiz_questions BEFORE UPDATE ON quiz_questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_practice_sessions BEFORE INSERT OR UPDATE OR DELETE ON practice_sessions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_practice_sessions BEFORE UPDATE ON practice_sessions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_practice_session_questions BEFORE INSERT OR UPDATE OR DELETE ON practice_session_questions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_practice_session_questions BEFORE UPDATE ON practice_session_questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_practice_answers BEFORE INSERT OR UPDATE OR DELETE ON practice_answers FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_practice_answers BEFORE UPDATE ON practice_answers FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_ai_conversations BEFORE INSERT OR UPDATE OR DELETE ON ai_conversations FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_ai_conversations BEFORE UPDATE ON ai_conversations FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_ai_messages BEFORE INSERT OR UPDATE OR DELETE ON ai_messages FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_ai_messages BEFORE UPDATE ON ai_messages FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_assignments BEFORE INSERT OR UPDATE OR DELETE ON assignments FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_assignments BEFORE UPDATE ON assignments FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_assignment_recipients BEFORE INSERT OR UPDATE OR DELETE ON assignment_recipients FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_assignment_recipients BEFORE UPDATE ON assignment_recipients FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_assignment_questions BEFORE INSERT OR UPDATE OR DELETE ON assignment_questions FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_assignment_questions BEFORE UPDATE ON assignment_questions FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_attempts BEFORE INSERT OR UPDATE OR DELETE ON attempts FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_attempts BEFORE UPDATE ON attempts FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_attempt_answers BEFORE INSERT OR UPDATE OR DELETE ON attempt_answers FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_attempt_answers BEFORE UPDATE ON attempt_answers FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
CREATE TRIGGER v2_serialize_audit_events BEFORE INSERT OR UPDATE OR DELETE ON audit_events FOR EACH STATEMENT EXECUTE FUNCTION quizai_v2_serialize();
CREATE TRIGGER identity_audit_events BEFORE UPDATE ON audit_events FOR EACH ROW EXECUTE FUNCTION quizai_v2_identity_guard();
