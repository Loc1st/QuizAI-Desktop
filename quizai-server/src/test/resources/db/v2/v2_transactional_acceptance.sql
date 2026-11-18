-- Future authorized TEST execution only. Rollback on EVERY failure; compare business data before/after.
BEGIN;
DO $$
DECLARE
    teacher uuid; student uuid; cid uuid; mid uuid; bank uuid; q uuid; qv uuid; quiz uuid;
    aid uuid; rid uuid; aq uuid; snapshot_ids uuid[] := ARRAY[]::uuid[]; versions uuid[] := ARRAY[]::uuid[];
    start_request uuid; submit_request uuid; a attempts%ROWTYPE; retry attempts%ROWTYPE;
    saved attempt_answers%ROWTYPE; duplicate_saved attempt_answers%ROWTYPE;
    round_no integer; pos integer; target_correct integer; expected numeric[] := ARRAY[0.5,0.9,0.7];
    targets integer[] := ARRAY[5,9,7]; best numeric; n integer; rejected boolean;
    before_start timestamptz; projected record; doc uuid; conv uuid;
    pquiz uuid; pq uuid; pv uuid; popt uuid; ps uuid; pitem uuid;
    tag text := replace(gen_random_uuid()::text,'-','');
BEGIN
    IF current_database() <> 'quizai_v2_test' THEN
        RAISE EXCEPTION 'Acceptance fixtures may run ONLY on quizai_v2_test';
    END IF;
    INSERT INTO users(email_normalized,password_hash,role) VALUES('v2.t.'||tag||'@example.invalid',repeat('x',64),'TEACHER') RETURNING id INTO teacher;
    INSERT INTO users(email_normalized,password_hash,role) VALUES('v2.s.'||tag||'@example.invalid',repeat('y',64),'STUDENT') RETURNING id INTO student;
    INSERT INTO classes(teacher_id,name,join_code_hmac,join_enabled) VALUES(teacher,'Rollback class',tag,true) RETURNING id INTO cid;
    INSERT INTO class_members(class_id,student_id) VALUES(cid,student) RETURNING id INTO mid;
    INSERT INTO question_banks(owner_teacher_id,name) VALUES(teacher,'Rollback bank') RETURNING id INTO bank;
    INSERT INTO quizzes(owner_id,kind,title,status) VALUES(teacher,'TEACHER_QUIZ','Rollback quiz','PUBLISHED') RETURNING id INTO quiz;
    FOR pos IN 1..10 LOOP
        INSERT INTO questions(owner_id,bank_id,origin) VALUES(teacher,bank,'MANUAL') RETURNING id INTO q;
        INSERT INTO question_versions(question_id,version_no,stem) VALUES(q,1,'Acceptance question '||pos) RETURNING id INTO qv;
        INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(qv,1,'correct',true),(qv,2,'incorrect',false);
        IF pos=1 THEN
            INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(quiz,qv,pos);
            rejected := false;
            BEGIN UPDATE question_versions SET stem='tampered used draft' WHERE id=qv;
            EXCEPTION WHEN check_violation THEN rejected := true; END;
            IF NOT rejected THEN RAISE EXCEPTION 'Used draft content must freeze while owner approval remains possible'; END IF;
        END IF;
        UPDATE question_versions SET approval_status='APPROVED',approved_by=teacher,approved_at=clock_timestamp() WHERE id=qv;
        IF pos>1 THEN INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(quiz,qv,pos); END IF;
        versions := array_append(versions,qv);
    END LOOP;
    INSERT INTO assignments(class_id,teacher_id,quiz_id,title,opens_at,closes_at,duration_minutes,max_attempts,score_release_at,answer_release_at)
        VALUES(cid,teacher,quiz,'Three attempts',clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 day',20,3,
               clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 day') RETURNING id INTO aid;
    INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES(aid,student,mid) RETURNING id INTO rid;
    FOR pos IN 1..10 LOOP
        INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json)
            VALUES(aid,versions[pos],pos,jsonb_build_object('schema_version',1,'stem','Acceptance question '||pos,
                'options',jsonb_build_array(jsonb_build_object('key','1','text','correct'),jsonb_build_object('key','2','text','incorrect')),
                'correct_option_key','1')) RETURNING id INTO aq;
        snapshot_ids := array_append(snapshot_ids,aq);
    END LOOP;
    UPDATE assignments SET status='PUBLISHED' WHERE id=aid;
    SELECT max(t.normalized_score) INTO best FROM attempts t WHERE t.recipient_id=rid AND t.status IN ('SUBMITTED','TIMED_OUT');
    IF best IS NOT NULL THEN RAISE EXCEPTION 'No terminal attempt must mean NULL, not zero'; END IF;
    FOR round_no IN 1..3 LOOP
        start_request := gen_random_uuid(); submit_request := gen_random_uuid(); before_start := clock_timestamp();
        SELECT * INTO a FROM quizai_v2_start_attempt(student,rid,start_request);
        SELECT * INTO retry FROM quizai_v2_start_attempt(student,rid,start_request);
        IF retry IS DISTINCT FROM a OR a.attempt_no <> round_no OR a.started_at < before_start OR a.status <> 'IN_PROGRESS'
           OR a.deadline_at <> least((SELECT x.closes_at FROM assignments x WHERE x.id=aid),a.started_at+interval '20 minute') THEN
            RAISE EXCEPTION 'Start must be idempotent and derive server time/deadline';
        END IF;
        SELECT count(*) INTO n FROM quizai_v2_student_questions(student,a.id);
        IF n <> 10 THEN RAISE EXCEPTION 'Student projection lost questions'; END IF;
        FOR projected IN SELECT * FROM quizai_v2_student_questions(student,a.id) LOOP
            IF jsonb_typeof(projected.options) IS DISTINCT FROM 'array' OR jsonb_array_length(projected.options) <> 2
               OR EXISTS(SELECT 1 FROM jsonb_array_elements(projected.options) item
                         WHERE (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(item) k) IS DISTINCT FROM ARRAY['key','text']) THEN
                RAISE EXCEPTION 'Student options must contain only key/text';
            END IF;
        END LOOP;
        target_correct := targets[round_no];
        FOR pos IN 1..10 LOOP
            SELECT * INTO saved FROM quizai_v2_save_answer(student,a.id,snapshot_ids[pos],CASE WHEN pos<=target_correct THEN '1' ELSE '2' END,1);
            SELECT * INTO duplicate_saved FROM quizai_v2_save_answer(student,a.id,snapshot_ids[pos],CASE WHEN pos<=target_correct THEN '1' ELSE '2' END,1);
            IF duplicate_saved IS DISTINCT FROM saved THEN RAISE EXCEPTION 'Duplicate autosave must preserve original ACK id/time'; END IF;
        END LOOP;
        PERFORM quizai_v2_save_answer(student,a.id,snapshot_ids[1],NULL,2);
        PERFORM quizai_v2_save_answer(student,a.id,snapshot_ids[1],'1',3);
        rejected := false;
        BEGIN PERFORM quizai_v2_save_answer(student,a.id,snapshot_ids[1],'2',2);
        EXCEPTION WHEN check_violation THEN rejected := true; END;
        IF NOT rejected THEN RAISE EXCEPTION 'Old autosave revision must be rejected'; END IF;
        SELECT * INTO saved FROM attempt_answers aa WHERE aa.attempt_id=a.id AND aa.assignment_question_id=snapshot_ids[1];
        IF saved.answer_revision <> 3 OR saved.selected_option_key <> '1' THEN RAISE EXCEPTION 'Old request overwrote ACK'; END IF;
        SELECT * INTO a FROM quizai_v2_finalize_attempt(student,a.id,submit_request,repeat('f',64));
        SELECT * INTO retry FROM quizai_v2_finalize_attempt(student,a.id,submit_request,repeat('f',64));
        IF retry IS DISTINCT FROM a OR a.status <> 'SUBMITTED' OR a.earned_points <> target_correct
           OR a.possible_points <> 10 OR a.normalized_score <> expected[round_no] THEN
            RAISE EXCEPTION 'Finalization must grade ACK answers once and return same retry result';
        END IF;
    END LOOP;
    SELECT max(t.normalized_score),count(*) INTO best,n FROM attempts t WHERE t.recipient_id=rid AND t.status IN ('SUBMITTED','TIMED_OUT');
    IF best <> 0.900000 OR n <> 3 THEN RAISE EXCEPTION 'Scores 50/90/70 must retain three attempts and aggregate to 90 percent'; END IF;
    rejected := false;
    BEGIN PERFORM quizai_v2_start_attempt(student,rid,gen_random_uuid()); EXCEPTION WHEN check_violation THEN rejected := true; END;
    IF NOT rejected THEN RAISE EXCEPTION 'max_attempts must be enforced'; END IF;
    rejected := false;
    BEGIN UPDATE assignments SET score_release_at=clock_timestamp()+interval '2 day' WHERE id=aid;
    EXCEPTION WHEN check_violation THEN rejected := true; END;
    IF NOT rejected THEN RAISE EXCEPTION 'Eligible score release must not be postponed before first GET'; END IF;
    UPDATE assignments SET answer_release_at=clock_timestamp()+interval '2 day' WHERE id=aid;

    -- Actual clock expiry, never fabricated/backdated attempts. Both blank and ACKed timeout cases.
    FOR round_no IN 0..1 LOOP
        INSERT INTO assignments(class_id,teacher_id,quiz_id,title,opens_at,closes_at,duration_minutes,max_attempts)
            VALUES(cid,teacher,quiz,'Clock timeout '||round_no,clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 second',20,1) RETURNING id INTO aid;
        INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES(aid,student,mid) RETURNING id INTO rid;
        INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json)
            SELECT aid,x.source_question_version_id,1,x.snapshot_json FROM assignment_questions x WHERE x.id=snapshot_ids[1] RETURNING id INTO aq;
        UPDATE assignments SET status='PUBLISHED' WHERE id=aid;
        SELECT * INTO a FROM quizai_v2_start_attempt(student,rid,gen_random_uuid());
        IF round_no=1 THEN PERFORM quizai_v2_save_answer(student,a.id,aq,'1',1); END IF;
        PERFORM pg_sleep(1.10);
        rejected := false;
        BEGIN PERFORM quizai_v2_save_answer(student,a.id,aq,'2',2); EXCEPTION WHEN check_violation THEN rejected := true; END;
        IF NOT rejected THEN RAISE EXCEPTION 'No new answer may be accepted after server deadline'; END IF;
        rejected := false;
        BEGIN
            UPDATE attempts SET status='SUBMITTED',submitted_at=a.started_at,submit_request_id=gen_random_uuid(),
                submitted_digest=repeat('f',64),earned_points=round_no,possible_points=1,normalized_score=round_no WHERE id=a.id;
        EXCEPTION WHEN check_violation THEN rejected := true; END;
        IF NOT rejected THEN RAISE EXCEPTION 'Backdated submitted_at must not bypass server deadline'; END IF;
        rejected := false;
        BEGIN
            UPDATE attempts SET status='TIMED_OUT',submitted_at=clock_timestamp(),earned_points=NULL,
                possible_points=1,normalized_score=NULL WHERE id=a.id;
        EXCEPTION WHEN check_violation THEN rejected := true; END;
        IF NOT rejected THEN RAISE EXCEPTION 'Expired timeout still requires nonNULL result points'; END IF;
        SELECT * INTO a FROM quizai_v2_finalize_attempt(student,a.id,gen_random_uuid(),repeat('a',64));
        SELECT * INTO retry FROM quizai_v2_finalize_attempt(student,a.id,gen_random_uuid(),repeat('b',64));
        IF a IS DISTINCT FROM retry OR a.status <> 'TIMED_OUT' OR a.earned_points <> round_no OR a.possible_points <> 1
           OR a.normalized_score <> round_no OR a.submit_request_id IS NOT NULL OR a.submitted_digest IS NOT NULL THEN
            RAISE EXCEPTION 'Timeout must use ACK answers only; blank timeout must be valid zero';
        END IF;
    END LOOP;

    INSERT INTO documents(owner_id,input_type,storage_key,extracted_text_storage_key,extraction_status)
        VALUES(student,'PDF','private/'||tag,'private/text/'||tag,'READY') RETURNING id INTO doc;
    INSERT INTO ai_conversations(owner_student_id,conversation_type,pdf_document_id) VALUES(student,'PDF_TUTOR',doc) RETURNING id INTO conv;
    INSERT INTO ai_messages(conversation_id,role,content) VALUES(conv,'USER','Own PDF question'),(conv,'ASSISTANT','Own PDF context');
    INSERT INTO questions(owner_id,origin) VALUES(student,'MANUAL') RETURNING id INTO pq;
    INSERT INTO question_versions(question_id,version_no,stem) VALUES(pq,1,'Private practice') RETURNING id INTO pv;
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(pv,1,'correct',true) RETURNING id INTO popt;
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(pv,2,'incorrect',false);
    INSERT INTO quizzes(owner_id,kind,title) VALUES(student,'PRACTICE','Private practice') RETURNING id INTO pquiz;
    INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(pquiz,pv,1);
    INSERT INTO practice_sessions(student_id,quiz_id) VALUES(student,pquiz) RETURNING id INTO ps;
    INSERT INTO practice_session_questions(practice_session_id,question_version_id,position) VALUES(ps,pv,1) RETURNING id INTO pitem;
    INSERT INTO practice_answers(session_question_id,selected_option_id,is_correct,answered_at) VALUES(pitem,popt,true,clock_timestamp());
    UPDATE practice_sessions SET status='COMPLETED',finished_at=clock_timestamp(),correct_count=1,total_questions=1 WHERE id=ps;
    IF NOT EXISTS(SELECT 1 FROM practice_sessions p WHERE p.id=ps AND p.status='COMPLETED' AND p.correct_count=1 AND p.total_questions=1) THEN
        RAISE EXCEPTION 'Practice completion needs nonNULL counts';
    END IF;
    INSERT INTO audit_events(actor_id,event_type,resource_type,resource_id,outcome) VALUES(teacher,'ACCEPTANCE','ASSIGNMENT',aid,'SUCCESS');
    RAISE NOTICE 'V2 positive assertions passed; transaction rollback still required; no API/UI evidence';
END $$;
ROLLBACK;
