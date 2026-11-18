-- Future authorized TEST execution only. Every fixture/helper is inside BEGIN/ROLLBACK.
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'quizai_v2_test' THEN
        RAISE EXCEPTION 'Negative acceptance may run ONLY on quizai_v2_test';
    END IF;
END $$;
CREATE FUNCTION pg_temp.v2_reject(command text,label text,states text[] DEFAULT ARRAY['23514']) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE actual_state text;
BEGIN
    BEGIN
        EXECUTE command;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
        IF actual_state = ANY(states) THEN RETURN; END IF;
        RAISE EXCEPTION 'Unexpected SQLSTATE % for negative assertion %',actual_state,label;
    END;
    RAISE EXCEPTION 'Expected rejection: %',label;
END $$;
DO $$
DECLARE
    ta uuid; tb uuid; sa uuid; sb uuid; ca uuid; cb uuid; ma uuid; mb uuid; rejoined uuid;
    bank uuid; qa uuid; va uuid; qb uuid; vb uuid; opta uuid; optb uuid;
    tq uuid; sq uuid; sq2 uuid; pq uuid; pv uuid; pva uuid; pvb uuid; ps uuid; ps2 uuid; pi uuid; pi2 uuid; pa uuid;
    doca uuid; docb uuid; conv uuid; msg uuid; audit_id uuid; sess uuid;
    aid uuid; aid2 uuid; rid uuid; aq uuid; aq2 uuid; a attempts%ROWTYPE; direct_attempt attempts%ROWTYPE;
    request_id uuid; digest text := repeat('f',64); snapshot jsonb;
    invalid_doc jsonb; key_name text; candidate jsonb; v jsonb;
    field_name text; tag text := replace(gen_random_uuid()::text,'-',''); before_insert timestamptz;
BEGIN
    IF current_database() <> 'quizai_v2_test' THEN RAISE EXCEPTION 'TEST database only'; END IF;
    INSERT INTO users(email_normalized,password_hash,role) VALUES('v2.na.'||tag||'@example.invalid',repeat('x',64),'TEACHER') RETURNING id INTO ta;
    INSERT INTO users(email_normalized,password_hash,role) VALUES('v2.nb.'||tag||'@example.invalid',repeat('x',64),'TEACHER') RETURNING id INTO tb;
    INSERT INTO users(email_normalized,password_hash,role) VALUES('v2.nc.'||tag||'@example.invalid',repeat('y',64),'STUDENT') RETURNING id INTO sa;
    INSERT INTO users(email_normalized,password_hash,role) VALUES('v2.nd.'||tag||'@example.invalid',repeat('y',64),'STUDENT') RETURNING id INTO sb;
    INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(sa,repeat('a',64),clock_timestamp()+interval '1 hour') RETURNING id INTO sess;
    PERFORM pg_temp.v2_reject(format('UPDATE auth_sessions SET user_id=%L WHERE id=%L',sb,sess),'session principal reparent');
    UPDATE auth_sessions SET revoked_at=clock_timestamp() WHERE id=sess;
    PERFORM pg_temp.v2_reject(format('UPDATE auth_sessions SET revoked_at=NULL WHERE id=%L',sess),'session revocation restore');
    PERFORM pg_temp.v2_reject(format('INSERT INTO users(email_normalized,password_hash,role) VALUES(%L,repeat(''x'',64),''STUDENT'')','v2.na.'||tag||'@example.invalid'),'duplicate email',ARRAY['23505']);
    PERFORM pg_temp.v2_reject(format('UPDATE users SET role=''TEACHER'' WHERE id=%L',sa),'role escalation');
    PERFORM pg_temp.v2_reject(format('INSERT INTO classes(teacher_id,name) VALUES(%L,''student class'')',sa),'student cannot own teacher class');
    INSERT INTO classes(teacher_id,name,join_code_hmac,join_enabled) VALUES(ta,'Negative A',tag,true) RETURNING id INTO ca;
    INSERT INTO classes(teacher_id,name) VALUES(tb,'Negative B') RETURNING id INTO cb;
    PERFORM pg_temp.v2_reject(format('INSERT INTO classes(teacher_id,name,join_code_hmac,join_enabled) VALUES(%L,''duplicate join'',%L,true)',tb,tag),'enabled join code duplicate',ARRAY['23505']);
    INSERT INTO class_members(class_id,student_id) VALUES(ca,sa) RETURNING id INTO ma;
    INSERT INTO class_members(class_id,student_id) VALUES(cb,sb) RETURNING id INTO mb;
    PERFORM pg_temp.v2_reject(format('INSERT INTO class_members(class_id,student_id) VALUES(%L,%L)',ca,sa),'one active membership',ARRAY['23505']);
    PERFORM pg_temp.v2_reject(format('INSERT INTO class_members(class_id,student_id) VALUES(%L,%L)',ca,ta),'teacher cannot be student member');
    INSERT INTO documents(owner_id,input_type,storage_key) VALUES(sa,'PDF','private/a/'||tag) RETURNING id INTO doca;
    INSERT INTO documents(owner_id,input_type,storage_key) VALUES(sb,'PDF','private/b/'||tag) RETURNING id INTO docb;
    PERFORM pg_temp.v2_reject(format('UPDATE documents SET owner_id=%L WHERE id=%L',sb,doca),'document parent owner reparent');
    PERFORM pg_temp.v2_reject(format('UPDATE documents SET input_type=''TXT'' WHERE id=%L',doca),'PDF parent context type reparent');
    PERFORM pg_temp.v2_reject(format('INSERT INTO questions(owner_id,source_document_id,origin) VALUES(%L,%L,''AI'')',sa,docb),'foreign document source');
    INSERT INTO question_banks(owner_teacher_id,name) VALUES(ta,'Negative bank') RETURNING id INTO bank;
    PERFORM pg_temp.v2_reject(format('INSERT INTO questions(owner_id,bank_id,origin) VALUES(%L,%L,''MANUAL'')',tb,bank),'foreign teacher bank',ARRAY['23503','23514']);
    INSERT INTO questions(owner_id,bank_id,origin) VALUES(ta,bank,'MANUAL') RETURNING id INTO qa;
    INSERT INTO question_versions(question_id,version_no,stem) VALUES(qa,1,'Negative source') RETURNING id INTO va;
    PERFORM pg_temp.v2_reject(format('UPDATE question_versions SET approval_status=''APPROVED'',approved_by=%L,approved_at=clock_timestamp() WHERE id=%L',ta,va),'approve with no options');
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(va,1,'correct',true) RETURNING id INTO opta;
    PERFORM pg_temp.v2_reject(format('UPDATE question_versions SET approval_status=''APPROVED'',approved_by=%L,approved_at=clock_timestamp() WHERE id=%L',ta,va),'approve with one option');
    PERFORM pg_temp.v2_reject(format('INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(%L,2,''second correct'',true)',va),'multiple correct options',ARRAY['23505','23514']);
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(va,2,'incorrect',false);
    PERFORM pg_temp.v2_reject(format('UPDATE question_versions SET approval_status=''APPROVED'',approved_by=%L,approved_at=clock_timestamp() WHERE id=%L',tb,va),'wrong teacher reviewer');
    UPDATE question_options SET is_correct=false WHERE id=opta;
    PERFORM pg_temp.v2_reject(format('UPDATE question_versions SET approval_status=''APPROVED'',approved_by=%L,approved_at=clock_timestamp() WHERE id=%L',ta,va),'approve with zero correct');
    UPDATE question_options SET is_correct=true WHERE id=opta;
    UPDATE question_versions SET approval_status='APPROVED',approved_by=ta,approved_at=clock_timestamp() WHERE id=va;
    PERFORM pg_temp.v2_reject(format('UPDATE question_versions SET stem=''tamper'' WHERE id=%L',va),'approved version edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM question_versions WHERE id=%L',va),'approved version delete');
    PERFORM pg_temp.v2_reject(format('UPDATE question_options SET option_text=''tamper'' WHERE id=%L',opta),'approved option edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM question_options WHERE id=%L',opta),'approved option delete');
    INSERT INTO questions(owner_id,origin) VALUES(tb,'MANUAL') RETURNING id INTO qb;
    INSERT INTO question_versions(question_id,version_no,stem) VALUES(qb,1,'Other source') RETURNING id INTO vb;
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(vb,1,'correct',true),(vb,2,'incorrect',false);
    UPDATE question_versions SET approval_status='APPROVED',approved_by=tb,approved_at=clock_timestamp() WHERE id=vb;
    INSERT INTO quizzes(owner_id,kind,title) VALUES(ta,'TEACHER_QUIZ','Negative teacher quiz') RETURNING id INTO tq;
    INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(tq,va,1);
    PERFORM pg_temp.v2_reject(format('INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(%L,%L,2)',tq,vb),'quiz foreign version');
    PERFORM pg_temp.v2_reject(format('UPDATE questions SET owner_id=%L WHERE id=%L',tb,qa),'reparent question owner',ARRAY['23514','23503']);
    PERFORM pg_temp.v2_reject(format('UPDATE question_banks SET owner_teacher_id=%L WHERE id=%L',tb,bank),'reparent bank owner',ARRAY['23514','23503']);
    PERFORM pg_temp.v2_reject(format('UPDATE quizzes SET owner_id=%L WHERE id=%L',tb,tq),'reparent quiz owner',ARRAY['23514','23503']);
    PERFORM pg_temp.v2_reject(format('UPDATE classes SET teacher_id=%L WHERE id=%L',tb,ca),'reparent class owner');
    INSERT INTO assignments(class_id,teacher_id,quiz_id,title,opens_at,closes_at,duration_minutes,max_attempts,answer_release_at,answer_release_mode)
        VALUES(ca,ta,tq,'Negative assignment',clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 day',20,5,
               clock_timestamp()-interval '1 minute','AFTER_VALID_SUBMISSION') RETURNING id INTO aid;
    PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES(%L,%L,%L)',aid,sb,ma),'recipient student/episode mismatch');
    PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES(%L,%L,%L)',aid,sb,mb),'recipient class/episode mismatch');
    INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES(aid,sa,ma) RETURNING id INTO rid;
    snapshot := '{"schema_version":1,"stem":"Negative source","options":[{"key":"1","text":"correct"},{"key":"2","text":"incorrect"}],"correct_option_key":"1"}'::jsonb;
    IF quizai_v2_snapshot_valid(snapshot) IS DISTINCT FROM true THEN RAISE EXCEPTION 'Valid snapshot rejected'; END IF;
    IF quizai_v2_snapshot_valid(NULL) IS DISTINCT FROM false THEN RAISE EXCEPTION 'SQL NULL snapshot accepted'; END IF;
    -- Each missing, JSON null, wrong type must return FALSE, never SQL UNKNOWN accepted by CHECK.
    FOREACH key_name IN ARRAY ARRAY['schema_version','stem','options','correct_option_key'] LOOP
        FOR candidate IN SELECT snapshot-key_name UNION ALL SELECT jsonb_set(snapshot,ARRAY[key_name],'null') LOOP
            IF quizai_v2_snapshot_valid(candidate) IS DISTINCT FROM false THEN RAISE EXCEPTION 'Missing/null snapshot field accepted: %',key_name; END IF;
            PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(%L,%L,1,%L::jsonb)',aid,va,candidate),'missing/null snapshot field '||key_name);
        END LOOP;
        FOR v IN SELECT value FROM jsonb_array_elements('[true,42,{},[]]') LOOP
            IF (key_name='schema_version' AND v='42'::jsonb) OR jsonb_typeof(v) IS DISTINCT FROM jsonb_typeof(snapshot->key_name) THEN
                candidate := jsonb_set(snapshot,ARRAY[key_name],v);
                IF quizai_v2_snapshot_valid(candidate) IS DISTINCT FROM false THEN RAISE EXCEPTION 'Wrong snapshot field type accepted: %',key_name; END IF;
                PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(%L,%L,1,%L::jsonb)',aid,va,candidate),'wrong snapshot type '||key_name);
            END IF;
        END LOOP;
    END LOOP;
    FOREACH key_name IN ARRAY ARRAY['key','text'] LOOP
        FOR v IN SELECT value FROM jsonb_array_elements('[null,true,42,{},[]]') LOOP
            candidate := jsonb_set(snapshot,ARRAY['options','0',key_name],v);
            IF quizai_v2_snapshot_valid(candidate) IS DISTINCT FROM false THEN RAISE EXCEPTION 'Wrong option field type accepted: %',key_name; END IF;
            PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(%L,%L,1,%L::jsonb)',aid,va,candidate),'wrong option type '||key_name);
        END LOOP;
    END LOOP;
    FOR invalid_doc IN SELECT value FROM jsonb_array_elements(jsonb_build_array(
        'null'::jsonb,'[]'::jsonb,'true'::jsonb,'"text"'::jsonb,
        jsonb_set(snapshot,'{schema_version}','"1"'),jsonb_set(snapshot,'{schema_version}','1.5'),
        jsonb_set(snapshot,'{stem}','" "'),jsonb_set(snapshot,'{correct_option_key}','"missing"'),
        jsonb_set(snapshot,'{correct_option_key}','" "'),jsonb_set(snapshot,'{options}','[]'),
        jsonb_set(snapshot,'{options}','[{"key":"1","text":"correct"}]'),
        jsonb_set(snapshot,'{options}','[{"key":"1","text":"correct"},{"key":"1","text":"duplicate"}]'),
        jsonb_set(snapshot,'{options}','[null,{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{},{}]'),
        jsonb_set(snapshot,'{options}','[{"text":"correct"},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":"1"},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":null,"text":"correct"},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":"1","text":null},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":1,"text":"correct"},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":"1","text":{}},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":" ","text":"correct"},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":"1","text":" "},{"key":"2","text":"incorrect"}]'),
        jsonb_set(snapshot,'{options}','[{"key":"1","text":"correct","is_correct":true},{"key":"2","text":"incorrect"}]'),
        snapshot||'{"answer":"1"}'::jsonb)) LOOP
        IF quizai_v2_snapshot_valid(invalid_doc) IS DISTINCT FROM false THEN RAISE EXCEPTION 'Invalid snapshot must return false, never NULL'; END IF;
        PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(%L,%L,1,%L::jsonb)',aid,va,invalid_doc),'malformed snapshot',ARRAY['23514','23502']);
    END LOOP;
    PERFORM pg_temp.v2_reject(format('INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(%L,%L,1,%L::jsonb)',aid,vb,snapshot),'foreign teacher snapshot');
    INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(aid,va,1,snapshot) RETURNING id INTO aq;
    INSERT INTO quizzes(owner_id,kind,title) VALUES(ta,'TEACHER_QUIZ','Other valid draft template') RETURNING id INTO sq2;
    PERFORM pg_temp.v2_reject(format('UPDATE assignments SET quiz_id=%L WHERE id=%L',sq2,aid),'populated draft parent change');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_publish_assignment(%L,%L)',tb,aid),'foreign teacher publication');
    PERFORM quizai_v2_publish_assignment(ta,aid);
    PERFORM pg_temp.v2_reject(format('UPDATE assignment_questions SET snapshot_json=jsonb_set(snapshot_json,''{stem}'',''"tamper"'') WHERE id=%L',aq),'published snapshot edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM assignment_questions WHERE id=%L',aq),'published snapshot delete');
    PERFORM pg_temp.v2_reject(format('UPDATE assignment_recipients SET student_id=%L WHERE id=%L',sb,rid),'recipient reparent');

    INSERT INTO assignments(class_id,teacher_id,quiz_id,title,opens_at,closes_at,duration_minutes,max_attempts)
        VALUES(ca,ta,tq,'Other assignment',clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 day',20,1) RETURNING id INTO aid2;
    INSERT INTO assignment_recipients(assignment_id,student_id,membership_id) VALUES(aid2,sa,ma);
    INSERT INTO assignment_questions(assignment_id,source_question_version_id,position,snapshot_json) VALUES(aid2,va,1,snapshot) RETURNING id INTO aq2;
    UPDATE assignments SET status='PUBLISHED' WHERE id=aid2;
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_start_attempt(%L,%L,gen_random_uuid())',sb,rid),'foreign student start');
    request_id := gen_random_uuid(); SELECT * INTO a FROM quizai_v2_start_attempt(sa,rid,request_id);
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_student_questions(%L,%L)',sb,a.id),'foreign student projection');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_save_answer(%L,%L,%L,''1'',1)',sb,a.id,aq),'foreign student save');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_finalize_attempt(%L,%L,gen_random_uuid(),%L)',sb,a.id,digest),'foreign student finalize');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_save_answer(%L,%L,%L,''1'',1)',sa,a.id,aq2),'cross assignment question');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_save_answer(%L,%L,%L,''invalid'',1)',sa,a.id,aq),'invalid selected option');
    PERFORM quizai_v2_save_answer(sa,a.id,aq,'1',3);
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_save_answer(%L,%L,%L,''2'',3)',sa,a.id,aq),'same revision different payload');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_save_answer(%L,%L,%L,''2'',2)',sa,a.id,aq),'out of order revision');
    PERFORM pg_temp.v2_reject(format('DELETE FROM attempt_answers WHERE attempt_id=%L',a.id),'answer delete');
    PERFORM pg_temp.v2_reject(format('DELETE FROM attempts WHERE id=%L',a.id),'in progress attempt delete');
    PERFORM pg_temp.v2_reject(format('UPDATE attempts SET started_at=started_at-interval ''1 year'' WHERE id=%L',a.id),'backdated existing attempt');
    FOREACH field_name IN ARRAY ARRAY['earned_points','possible_points','normalized_score'] LOOP
        PERFORM pg_temp.v2_reject(format('UPDATE attempts SET status=''SUBMITTED'',submitted_at=clock_timestamp(),submit_request_id=gen_random_uuid(),submitted_digest=%L,earned_points=%s,possible_points=%s,normalized_score=%s WHERE id=%L',
            digest,CASE WHEN field_name='earned_points' THEN 'NULL' ELSE '1' END,
            CASE WHEN field_name='possible_points' THEN 'NULL' ELSE '1' END,
            CASE WHEN field_name='normalized_score' THEN 'NULL' ELSE '1' END,a.id),'terminal partial NULL '||field_name);
    END LOOP;
    PERFORM pg_temp.v2_reject(format('UPDATE attempts SET status=''SUBMITTED'',submitted_at=clock_timestamp(),submit_request_id=gen_random_uuid(),submitted_digest=%L WHERE id=%L',digest,a.id),'all terminal points NULL');
    PERFORM pg_temp.v2_reject(format('UPDATE attempts SET status=''SUBMITTED'',submitted_at=clock_timestamp(),submit_request_id=gen_random_uuid(),submitted_digest=%L,earned_points=0,possible_points=1,normalized_score=0 WHERE id=%L',digest,a.id),'client-forged score differs from ACK');
    FOREACH key_name IN ARRAY ARRAY['','bad',repeat('F',64),repeat('a',63)] LOOP
        PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_finalize_attempt(%L,%L,gen_random_uuid(),%L)',sa,a.id,key_name),'invalid submit digest');
    END LOOP;

    -- No AI assistant response may be persisted while an assigned attempt is active.
    INSERT INTO ai_conversations(owner_student_id,conversation_type,pdf_document_id) VALUES(sa,'PDF_TUTOR',doca) RETURNING id INTO conv;
    PERFORM pg_temp.v2_reject(format('INSERT INTO ai_messages(conversation_id,role,content) VALUES(%L,''ASSISTANT'',''forbidden help'')',conv),'AI during assigned attempt');
    PERFORM pg_temp.v2_reject(format('INSERT INTO ai_conversations(owner_student_id,conversation_type,pdf_document_id) VALUES(%L,''PDF_TUTOR'',%L)',sa,docb),'foreign PDF tutor');
    SELECT * INTO a FROM quizai_v2_finalize_attempt(sa,a.id,gen_random_uuid(),digest);
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_finalize_attempt(%L,%L,gen_random_uuid(),%L)',sa,a.id,digest),'different submit request retry');
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_finalize_attempt(%L,%L,%L,%L)',sa,a.id,a.submit_request_id,repeat('b',64)),'same submit request different digest');
    PERFORM pg_temp.v2_reject(format('UPDATE attempts SET earned_points=0 WHERE id=%L',a.id),'terminal score edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM attempts WHERE id=%L',a.id),'terminal attempt delete');
    PERFORM pg_temp.v2_reject(format('UPDATE attempt_answers SET answer_revision=4,selected_option_key=''2'' WHERE attempt_id=%L',a.id),'terminal answer edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM attempt_answers WHERE attempt_id=%L',a.id),'terminal answer delete');
    PERFORM pg_temp.v2_reject(format('UPDATE assignments SET answer_release_at=NULL WHERE id=%L',aid),'eligible answer release retract');
    PERFORM pg_temp.v2_reject(format('UPDATE assignments SET answer_release_mode=''AFTER_ASSIGNMENT_CLOSE'' WHERE id=%L',aid),'eligible answer mode retract');

    before_insert := clock_timestamp();
    INSERT INTO attempts(assignment_id,recipient_id,attempt_no,start_request_id,started_at,deadline_at)
        VALUES(aid,rid,2,gen_random_uuid(),'1900-01-01','1900-01-02') RETURNING * INTO direct_attempt;
    IF direct_attempt.started_at < before_insert OR direct_attempt.deadline_at <> direct_attempt.started_at+interval '20 minute' THEN
        RAISE EXCEPTION 'Direct insert must override fabricated start/deadline with server time';
    END IF;
    UPDATE class_members SET left_at=clock_timestamp() WHERE id=ma;
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_start_attempt(%L,%L,gen_random_uuid())',sa,rid),'leave blocks new start');
    PERFORM quizai_v2_save_answer(sa,direct_attempt.id,aq,'1',1);
    PERFORM quizai_v2_finalize_attempt(sa,direct_attempt.id,gen_random_uuid(),digest);
    INSERT INTO class_members(class_id,student_id) VALUES(ca,sa) RETURNING id INTO rejoined;
    IF rejoined=ma THEN RAISE EXCEPTION 'Rejoin must create new membership episode'; END IF;
    PERFORM pg_temp.v2_reject(format('SELECT quizai_v2_start_attempt(%L,%L,gen_random_uuid())',sa,rid),'rejoin must not resurrect original recipient');

    INSERT INTO questions(owner_id,origin) VALUES(sa,'MANUAL') RETURNING id INTO pq;
    INSERT INTO question_versions(question_id,version_no,stem) VALUES(pq,1,'Private frozen practice') RETURNING id INTO pv;
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(pv,1,'yes',true) RETURNING id INTO pva;
    INSERT INTO question_options(question_version_id,position,option_text,is_correct) VALUES(pv,2,'no',false) RETURNING id INTO pvb;
    INSERT INTO quizzes(owner_id,kind,title) VALUES(sa,'PRACTICE','Negative private quiz') RETURNING id INTO sq;
    INSERT INTO quiz_questions(quiz_id,question_version_id,position) VALUES(sq,pv,1);
    INSERT INTO practice_sessions(student_id,quiz_id) VALUES(sa,sq) RETURNING id INTO ps;
    INSERT INTO practice_sessions(student_id,quiz_id) VALUES(sa,sq) RETURNING id INTO ps2;
    INSERT INTO practice_session_questions(practice_session_id,question_version_id,position) VALUES(ps,pv,1) RETURNING id INTO pi;
    INSERT INTO practice_session_questions(practice_session_id,question_version_id,position) VALUES(ps2,pv,1) RETURNING id INTO pi2;
    PERFORM pg_temp.v2_reject(format('UPDATE question_versions SET stem=''tampered history'' WHERE id=%L',pv),'practice draft version frozen');
    PERFORM pg_temp.v2_reject(format('UPDATE question_options SET option_text=''tamper'' WHERE id=%L',pva),'practice option frozen');
    PERFORM pg_temp.v2_reject(format('DELETE FROM question_options WHERE id=%L',pva),'practice option delete');
    PERFORM pg_temp.v2_reject(format('INSERT INTO practice_answers(session_question_id,selected_option_id,is_correct,answered_at) VALUES(%L,%L,true,clock_timestamp())',pi,opta),'practice foreign option');
    INSERT INTO practice_answers(session_question_id,selected_option_id,is_correct,answered_at) VALUES(pi,pva,true,clock_timestamp()) RETURNING id INTO pa;
    PERFORM pg_temp.v2_reject(format('INSERT INTO practice_session_questions(practice_session_id,question_version_id,position) VALUES(%L,%L,2)',ps,pv),'answered practice list insert');
    PERFORM pg_temp.v2_reject(format('UPDATE practice_session_questions SET position=2 WHERE id=%L',pi),'answered practice list edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM practice_session_questions WHERE id=%L',pi),'answered practice list delete');
    PERFORM pg_temp.v2_reject(format('UPDATE practice_answers SET session_question_id=%L WHERE id=%L',pi2,pa),'practice answer reparent');
    PERFORM pg_temp.v2_reject(format('UPDATE practice_sessions SET status=''COMPLETED'',finished_at=clock_timestamp() WHERE id=%L',ps),'completed practice all counts NULL');
    PERFORM pg_temp.v2_reject(format('UPDATE practice_sessions SET status=''COMPLETED'',finished_at=clock_timestamp(),total_questions=1 WHERE id=%L',ps),'completed practice correct NULL');
    PERFORM pg_temp.v2_reject(format('UPDATE practice_sessions SET status=''COMPLETED'',finished_at=clock_timestamp(),correct_count=1 WHERE id=%L',ps),'completed practice total NULL');
    UPDATE practice_sessions SET status='COMPLETED',finished_at=clock_timestamp(),correct_count=1,total_questions=1 WHERE id=ps;
    PERFORM pg_temp.v2_reject(format('UPDATE practice_sessions SET correct_count=0 WHERE id=%L',ps),'completed practice result edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM practice_sessions WHERE id=%L',ps),'completed practice delete');
    PERFORM pg_temp.v2_reject(format('UPDATE practice_answers SET selected_option_id=%L,is_correct=false WHERE id=%L',pvb,pa),'completed practice answer edit');
    PERFORM pg_temp.v2_reject(format('DELETE FROM practice_answers WHERE id=%L',pa),'completed practice answer delete');
    INSERT INTO ai_conversations(owner_student_id,conversation_type,practice_session_id) VALUES(sa,'PRACTICE_SESSION',ps) RETURNING id INTO conv;
    PERFORM pg_temp.v2_reject(format('INSERT INTO ai_conversations(owner_student_id,conversation_type,practice_session_id) VALUES(%L,''PRACTICE_SESSION'',%L)',sa,ps),'one chat per practice',ARRAY['23505']);
    PERFORM pg_temp.v2_reject(format('INSERT INTO ai_messages(conversation_id,role,content,practice_session_question_id) VALUES(%L,''USER'',''wrong context'',%L)',conv,pi2),'cross practice message context');
    INSERT INTO ai_messages(conversation_id,role,content,practice_session_question_id) VALUES(conv,'USER','Own completed practice',pi) RETURNING id INTO msg;
    PERFORM pg_temp.v2_reject(format('UPDATE ai_messages SET content=''changed'' WHERE id=%L',msg),'AI message append only update');
    PERFORM pg_temp.v2_reject(format('DELETE FROM ai_messages WHERE id=%L',msg),'AI message append only delete');
    INSERT INTO audit_events(actor_id,event_type,resource_type,outcome) VALUES(ta,'NEGATIVE_ACCEPTANCE','TEST','SUCCESS') RETURNING id INTO audit_id;
    PERFORM pg_temp.v2_reject(format('UPDATE audit_events SET outcome=''FAILED'' WHERE id=%L',audit_id),'audit update');
    PERFORM pg_temp.v2_reject(format('DELETE FROM audit_events WHERE id=%L',audit_id),'audit delete');
    RAISE NOTICE 'V2 negative assertions passed; transaction rollback still required; service/API permission tests remain separate';
END $$;
ROLLBACK;
