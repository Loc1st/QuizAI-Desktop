-- MUST run ONLY against isolated TEST DB migrated V1..V8; all test records ROLLBACK.
-- Schema/read-model regressions, not the service/concurrency acceptance suite T01..T30.
-- psql -X -v ON_ERROR_STOP=1 -f tests/postgresql_transactional_acceptance.sql
BEGIN;
DO $$
DECLARE
 teacher UUID:=gen_random_uuid(); student UUID:=gen_random_uuid(); outsider UUID:=gen_random_uuid();
 subject UUID:=gen_random_uuid();
 cl UUID:=gen_random_uuid(); mem UUID:=gen_random_uuid();
 bank UUID:=gen_random_uuid(); q UUID:=gen_random_uuid(); ver UUID:=gen_random_uuid(); draft_ver UUID:=gen_random_uuid();
 opt_ok UUID:=gen_random_uuid(); opt_bad UUID:=gen_random_uuid();
 quiz UUID:=gen_random_uuid(); ass UUID:=gen_random_uuid(); draft_ass UUID:=gen_random_uuid();
 draft_snap UUID:=gen_random_uuid();
 snap UUID:=gen_random_uuid(); so_ok UUID:=gen_random_uuid(); so_bad UUID:=gen_random_uuid();
 recipient UUID:=gen_random_uuid(); t1 UUID:=gen_random_uuid(); t2 UUID:=gen_random_uuid(); t3 UUID:=gen_random_uuid();
 c1 UUID:=gen_random_uuid(); c2 UUID:=gen_random_uuid(); clate UUID:=gen_random_uuid();
 blocked BOOLEAN; total NUMERIC(12,8); norm NUMERIC(12,8); constraint_hit TEXT;
 event_id UUID:=gen_random_uuid(); table_to_check TEXT; assignment_count INTEGER;
 private_q UUID:=gen_random_uuid(); private_ver UUID:=gen_random_uuid(); private_opt UUID:=gen_random_uuid();
 private_quiz UUID:=gen_random_uuid(); ps_inside UUID:=gen_random_uuid(); ps_before UUID:=gen_random_uuid();
 ps_after UUID:=gen_random_uuid(); ps_incomplete UUID:=gen_random_uuid(); overlapping_class UUID:=gen_random_uuid();
 stats RECORD;
BEGIN
 INSERT INTO users(id,email,password_hash,role,status) VALUES
 (teacher,'teacher.test.quizai@example.invalid','fake-only-for-transaction-test','TEACHER','ACTIVE'),
 (student,'student.test.quizai@example.invalid','fake-only-for-transaction-test','STUDENT','ACTIVE'),
 (outsider,'outsider.test.quizai@example.invalid','fake-only-for-transaction-test','STUDENT','ACTIVE');
 INSERT INTO subjects(id,code,name) VALUES(subject,'SQL-FIXTURE','Transactional fixture');
 INSERT INTO classes(id,teacher_id,name,join_code_digest,join_enabled,status)
 VALUES(cl,teacher,'SQL integration fixture','hmac-fixture-one',true,'ACTIVE');
 INSERT INTO class_members(id,class_id,student_id,joined_at)
 VALUES(mem,cl,student,CURRENT_TIMESTAMP-interval '1 day');
 blocked:=false;
 BEGIN
   INSERT INTO classes(id,teacher_id,name,join_code_digest,join_enabled,status)
   VALUES(gen_random_uuid(),teacher,'duplicate enabled code','hmac-fixture-one',true,'ACTIVE');
 EXCEPTION WHEN unique_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: active join code duplicate accepted'; END IF;
 -- Disabled code may reuse digest.
 INSERT INTO classes(id,teacher_id,name,join_code_digest,join_enabled,status)
 VALUES(gen_random_uuid(),teacher,'disabled reused code','hmac-fixture-one',false,'ACTIVE');
 blocked:=false;
 BEGIN
   INSERT INTO users(id,email,password_hash,role,status)
   VALUES(gen_random_uuid(),'UPPER.TEST@EXAMPLE.INVALID','hash','STUDENT','ACTIVE');
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: unnormalized email accepted'; END IF;

 INSERT INTO question_banks(id,owner_teacher_id,name) VALUES(bank,teacher,'Fixture bank');
 INSERT INTO questions(id,owner_id,bank_id) VALUES(q,teacher,bank);
 INSERT INTO question_versions(id,question_id,version_no,stem,subject_id,difficulty,approval_status)
 VALUES(ver,q,1,'2+2 = ?',subject,'EASY','DRAFT');
 INSERT INTO question_options(id,question_version_id,position,option_text,is_correct) VALUES
 (opt_ok,ver,1,'4',true),(opt_bad,ver,2,'5',false);
 UPDATE question_versions SET approval_status='APPROVED',approved_by=teacher,approved_at=CURRENT_TIMESTAMP WHERE id=ver;
 INSERT INTO question_versions(id,question_id,version_no,stem,difficulty)
 VALUES(draft_ver,q,2,'Unapproved teacher version','EASY');
 INSERT INTO question_options(id,question_version_id,position,option_text,is_correct) VALUES
 (gen_random_uuid(),draft_ver,1,'Yes',true),(gen_random_uuid(),draft_ver,2,'No',false);
 blocked:=false;
 BEGIN
   UPDATE question_versions SET approval_status='APPROVED',approved_by=teacher,approved_at=CURRENT_TIMESTAMP WHERE id=draft_ver;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: teacher approval without subject accepted'; END IF;
 blocked:=false;
 BEGIN
   UPDATE question_options SET option_text='changed' WHERE id=opt_ok;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: approved question was mutable'; END IF;
 INSERT INTO quizzes(id,owner_id,quiz_type,title,status) VALUES(quiz,teacher,'TEACHER_QUIZ','Fixture exam','READY');
 INSERT INTO quiz_questions(id,quiz_id,question_version_id,position) VALUES(gen_random_uuid(),quiz,ver,1);
 INSERT INTO assignments(id,class_id,teacher_id,source_quiz_id,status,opens_at,closes_at,
         duration_minutes,max_attempts,answer_release_mode,score_release_at)
 VALUES(ass,cl,teacher,quiz,'DRAFT',CURRENT_TIMESTAMP-interval '1 day',CURRENT_TIMESTAMP+interval '2 days',
         60,3,'AFTER_ASSIGNMENT_CLOSE',CURRENT_TIMESTAMP-interval '1 minute');
 INSERT INTO assignment_question_snapshots(id,assignment_id,source_question_version_id,position,stem,difficulty)
 VALUES(snap,ass,ver,1,'2+2=?','EASY');
 INSERT INTO assignment_snapshot_options(id,question_snapshot_id,position,option_text,is_correct) VALUES
 (so_ok,snap,1,'4',true),(so_bad,snap,2,'5',false);
 UPDATE assignments SET status='PUBLISHED' WHERE id=ass;
 INSERT INTO assignment_recipients(id,assignment_id,student_id,membership_id)
 VALUES(recipient,ass,student,mem);
 SELECT normalized_score INTO total FROM quizai_assignment_final_scores WHERE recipient_id=recipient;
 IF total IS NOT NULL OR quizai_can_view_score(recipient,student,CURRENT_TIMESTAMP) THEN
   RAISE EXCEPTION 'FAIL: no eligible attempt became a synthetic zero/released grade';
 END IF;
 INSERT INTO attempts(id,recipient_id,attempt_no,start_request_id,started_at,deadline_at,phase)
 VALUES(t1,recipient,1,'s1',CURRENT_TIMESTAMP-interval '2 hours',CURRENT_TIMESTAMP-interval '1 hour','CLOSED'),
       (t2,recipient,2,'s2',CURRENT_TIMESTAMP-interval '2 hours',CURRENT_TIMESTAMP-interval '1 hour','CLOSED');
 INSERT INTO submission_candidates(id,attempt_id,kind,captured_at,payload_digest) VALUES
 (c1,t1,'SERVER_TIMEOUT',CURRENT_TIMESTAMP,'digest-c1'),
 (c2,t2,'SERVER_TIMEOUT',CURRENT_TIMESTAMP,'digest-c2');
 INSERT INTO submission_candidate_answers(id,candidate_id,question_snapshot_id,selected_option_id)
 VALUES(gen_random_uuid(),c1,snap,so_ok);
 SELECT normalized_score INTO norm FROM quizai_calculate_candidate_score(c1);
 IF norm IS DISTINCT FROM 1.00000000 THEN RAISE EXCEPTION 'FAIL: candidate scoring wrong'; END IF;
 INSERT INTO submission_scores(id,candidate_id,earned_points,possible_points,normalized_score) VALUES
 (gen_random_uuid(),c1,1,1,1),(gen_random_uuid(),c2,0,1,0);
 UPDATE attempts SET effective_candidate_id=c1 WHERE id=t1;
 UPDATE attempts SET effective_candidate_id=c2 WHERE id=t2;
 SELECT normalized_score INTO total FROM quizai_assignment_final_scores WHERE recipient_id=recipient;
 IF total IS DISTINCT FROM 1.00000000 THEN RAISE EXCEPTION 'FAIL: max across attempts wrong'; END IF;
 blocked:=false;
 BEGIN
   UPDATE attempts SET effective_candidate_id=c2 WHERE id=t1;
 EXCEPTION WHEN foreign_key_violation THEN
   GET STACKED DIAGNOSTICS constraint_hit=CONSTRAINT_NAME;
   blocked:=constraint_hit='fk_attempt_effective_candidate_same_attempt';
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL DBX-16: cross-attempt candidate permitted'; END IF;
 -- This statement exercises the trigger rejection. The catalog script independently proves
 -- the actual composite FK columns, target, validation, and ON DELETE RESTRICT semantics.
 blocked:=false;
 BEGIN
   INSERT INTO attempts(id,recipient_id,attempt_no,start_request_id,started_at,deadline_at,phase,effective_candidate_id)
   VALUES(gen_random_uuid(),recipient,3,'invalid-effective-insert',CURRENT_TIMESTAMP-interval '2 hours',
     CURRENT_TIMESTAMP-interval '1 hour','CLOSED',c2);
 EXCEPTION WHEN foreign_key_violation THEN
   GET STACKED DIAGNOSTICS constraint_hit=CONSTRAINT_NAME;
   blocked:=constraint_hit='fk_attempt_effective_candidate_same_attempt';
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL DBX-16: cross-attempt candidate accepted on INSERT'; END IF;
 INSERT INTO submission_candidates(id,attempt_id,kind,captured_at,payload_digest) VALUES
 (clate,t1,'CLIENT_LATE',CURRENT_TIMESTAMP,'digest-late');
 INSERT INTO submission_scores(id,candidate_id,earned_points,possible_points,normalized_score)
 VALUES(gen_random_uuid(),clate,0,1,0);
 blocked:=false;
 BEGIN
   UPDATE attempts SET effective_candidate_id=clate WHERE id=t1;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: pending late candidate became effective'; END IF;
 INSERT INTO late_submission_reviews(id,candidate_id,teacher_id,decision,reason)
 VALUES(gen_random_uuid(),clate,teacher,'ACCEPT','Offline evidence approved');
 UPDATE attempts SET effective_candidate_id=clate WHERE id=t1;
 SELECT normalized_score INTO total FROM quizai_assignment_final_scores WHERE recipient_id=recipient;
 IF total IS DISTINCT FROM 0.00000000 THEN
      RAISE EXCEPTION 'FAIL: late score must replace base of same attempt, not take candidate max';
 END IF;
 blocked:=false;
 BEGIN
   UPDATE attempts SET effective_candidate_id=NULL WHERE id=t1;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: effective score was cleared'; END IF;
 IF NOT quizai_can_view_score(recipient,student,CURRENT_TIMESTAMP)
    OR quizai_can_view_score(recipient,outsider,CURRENT_TIMESTAMP)
    OR quizai_can_view_answers(recipient,student,CURRENT_TIMESTAMP) THEN
   RAISE EXCEPTION 'FAIL: score/answer publication independence or ownership';
 END IF;
 -- Open answers without opening score through the answer gate itself.
 UPDATE assignments SET answer_release_mode='AFTER_VALID_SUBMISSION',
   answer_release_at=CURRENT_TIMESTAMP-interval '1 minute' WHERE id=ass;
 IF NOT quizai_can_view_answers(recipient,student,CURRENT_TIMESTAMP)
    OR quizai_can_view_answers(recipient,outsider,CURRENT_TIMESTAMP) THEN
   RAISE EXCEPTION 'FAIL: answer ownership/release gate';
 END IF;
 SELECT count(*) INTO assignment_count FROM assignment_publication_events WHERE assignment_id=ass;
 IF assignment_count<>2 THEN RAISE EXCEPTION 'FAIL: independent FIRST_AVAILABLE markers missing'; END IF;
 INSERT INTO attempts(id,recipient_id,attempt_no,start_request_id,started_at,deadline_at)
 VALUES(t3,recipient,3,'s3',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '1 hour');
 IF quizai_can_view_answers(recipient,student,CURRENT_TIMESTAMP) THEN
   RAISE EXCEPTION 'FAIL: answers exposed during another active attempt';
 END IF;
 blocked:=false;
 BEGIN
   UPDATE assignments SET answer_release_at=NULL WHERE id=ass;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: previously available answers retracted during next attempt'; END IF;
 blocked:=false;
 BEGIN
   INSERT INTO submission_candidate_answers(id,candidate_id,question_snapshot_id,selected_option_id)
   VALUES(gen_random_uuid(),c2,snap,so_bad);
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: answer appended after scoring'; END IF;
 blocked:=false;
 BEGIN
   UPDATE submission_candidates SET payload_digest='tampered' WHERE id=clate;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: immutable candidate changed'; END IF;
 blocked:=false;
 BEGIN
   UPDATE assignments SET score_release_at=CURRENT_TIMESTAMP+interval '1 day' WHERE id=ass;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: released score can be postponed'; END IF;
 -- Published snapshots and the original recipient linkage cannot be rewritten through a draft parent.
 INSERT INTO assignments(id,class_id,teacher_id,source_quiz_id,opens_at,closes_at,
   duration_minutes,answer_release_mode)
 VALUES(draft_ass,cl,teacher,quiz,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '1 day',60,'AFTER_ASSIGNMENT_CLOSE');
 INSERT INTO assignment_question_snapshots(id,assignment_id,source_question_version_id,position,stem,difficulty)
 VALUES(draft_snap,draft_ass,draft_ver,1,'Draft fixture','EASY');
 INSERT INTO assignment_snapshot_options(id,question_snapshot_id,position,option_text,is_correct) VALUES
 (gen_random_uuid(),draft_snap,1,'Yes',true),(gen_random_uuid(),draft_snap,2,'No',false);
 blocked:=false;
 BEGIN
   UPDATE assignments SET status='PUBLISHED' WHERE id=draft_ass;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: unapproved source question published'; END IF;
 blocked:=false;
 BEGIN
   INSERT INTO assignments(id,class_id,teacher_id,source_quiz_id,status,opens_at,closes_at,duration_minutes,answer_release_mode)
   VALUES(gen_random_uuid(),cl,teacher,quiz,'PUBLISHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '1 day',60,'AFTER_ASSIGNMENT_CLOSE');
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: direct published assignment bypassed snapshot validation'; END IF;
 blocked:=false;
 BEGIN
   UPDATE assignments SET status='DRAFT' WHERE id=ass;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: published assignment returned to draft'; END IF;
 blocked:=false;
 BEGIN
   UPDATE assignment_question_snapshots SET assignment_id=draft_ass,position=2 WHERE id=snap;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: published question reparented'; END IF;
 blocked:=false;
 BEGIN
   UPDATE assignment_snapshot_options SET question_snapshot_id=draft_snap WHERE id=so_ok;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: published option reparented'; END IF;
 blocked:=false;
 BEGIN
   UPDATE assignment_recipients SET assigned_at=assigned_at+interval '1 second' WHERE id=recipient;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: original recipient linkage was mutable'; END IF;
 blocked:=false;
 BEGIN
   INSERT INTO assignment_recipient_score_events(id,recipient_id,old_earned,old_possible)
   VALUES(gen_random_uuid(),recipient,NULL,1);
 EXCEPTION WHEN check_violation THEN
   GET STACKED DIAGNOSTICS constraint_hit=CONSTRAINT_NAME;
   blocked:=constraint_hit='ck_recipient_old_score_pair';
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: partial-null score history accepted'; END IF;
 INSERT INTO assignment_recipient_score_events(id,recipient_id) VALUES(event_id,recipient);
 INSERT INTO attempt_result_events(id,attempt_id,old_candidate_id,new_candidate_id,reason_code,actor_id)
 VALUES(gen_random_uuid(),t1,c1,clate,'LATE_ACCEPT',teacher);
 INSERT INTO audit_events(id,actor_id,assignment_id,attempt_id,event_type,outcome)
 VALUES(gen_random_uuid(),teacher,ass,t1,'FIXTURE','ACCEPT');
 FOREACH table_to_check IN ARRAY ARRAY['submission_candidates','submission_candidate_answers','submission_scores',
   'late_submission_reviews','attempt_result_events','assignment_recipient_score_events','audit_events','assignment_publication_events']
 LOOP
   blocked:=false;
   BEGIN
     EXECUTE format('UPDATE %I SET id=id',table_to_check);
   EXCEPTION WHEN check_violation THEN blocked:=true; END;
   IF NOT blocked THEN RAISE EXCEPTION 'FAIL: immutable history update permitted: %',table_to_check; END IF;
 END LOOP;
 UPDATE class_members SET left_at=CURRENT_TIMESTAMP WHERE id=mem;
 blocked:=false;
 BEGIN
   UPDATE class_members SET left_at=NULL WHERE id=mem;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: closed membership episode reopened'; END IF;
 -- Completed sessions only, clipped to membership episodes, without double-counting
 -- a session that falls in two classes of the same teacher.
 INSERT INTO classes(id,teacher_id,name,join_enabled,status)
 VALUES(overlapping_class,teacher,'Overlapping fixture class',false,'ACTIVE');
 INSERT INTO class_members(id,class_id,student_id,joined_at,left_at)
 VALUES(gen_random_uuid(),overlapping_class,student,CURRENT_TIMESTAMP-interval '1 day',CURRENT_TIMESTAMP);
 INSERT INTO questions(id,owner_id) VALUES(private_q,student);
 INSERT INTO question_versions(id,question_id,version_no,stem,difficulty)
 VALUES(private_ver,private_q,1,'Private practice fixture','EASY');
 INSERT INTO question_options(id,question_version_id,position,option_text,is_correct) VALUES
 (private_opt,private_ver,1,'Correct',true),(gen_random_uuid(),private_ver,2,'Wrong',false);
 INSERT INTO quizzes(id,owner_id,quiz_type,title,status)
 VALUES(private_quiz,student,'PRACTICE','Private fixture','READY');
 INSERT INTO quiz_questions(id,quiz_id,question_version_id,position)
 VALUES(gen_random_uuid(),private_quiz,private_ver,1);
 blocked:=false;
 BEGIN
   UPDATE question_versions SET stem='Changed private source' WHERE id=private_ver;
 EXCEPTION WHEN check_violation THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'FAIL: used private source version changed'; END IF;
 INSERT INTO practice_sessions(id,student_id,quiz_id,started_at) VALUES
 (ps_inside,student,private_quiz,CURRENT_TIMESTAMP-interval '13 hours'),
 (ps_before,student,private_quiz,CURRENT_TIMESTAMP-interval '49 hours'),
 (ps_after,student,private_quiz,CURRENT_TIMESTAMP+interval '11 hours'),
 (ps_incomplete,student,private_quiz,CURRENT_TIMESTAMP-interval '1 hour');
 INSERT INTO practice_session_questions(id,practice_session_id,question_version_id,position)
 SELECT gen_random_uuid(),s.id,private_ver,1 FROM practice_sessions s
 WHERE s.id IN (ps_inside,ps_before,ps_after,ps_incomplete);
 INSERT INTO practice_answers(id,session_question_id,selected_option_id,answered_at,is_correct)
 SELECT gen_random_uuid(),sq.id,private_opt,ps.started_at+interval '1 minute',true
 FROM practice_session_questions sq JOIN practice_sessions ps ON ps.id=sq.practice_session_id
 WHERE ps.id IN (ps_inside,ps_before,ps_after);
 UPDATE practice_sessions SET status='COMPLETED',correct_count=1,total_questions=1,
   finished_at=CASE id WHEN ps_inside THEN CURRENT_TIMESTAMP-interval '12 hours'
     WHEN ps_before THEN CURRENT_TIMESTAMP-interval '2 days'
     ELSE CURRENT_TIMESTAMP+interval '12 hours' END
 WHERE id IN (ps_inside,ps_before,ps_after);
 SELECT * INTO stats FROM quizai_teacher_practice_aggregate(teacher,student,
   CURRENT_TIMESTAMP-interval '3 days',CURRENT_TIMESTAMP+interval '1 day');
 IF stats.completed_sessions<>1 OR stats.total_correct<>1 OR stats.total_questions<>1 THEN
   RAISE EXCEPTION 'FAIL: completed-only practice aggregate membership filtering/deduplication';
 END IF;
 SELECT * INTO stats FROM quizai_teacher_practice_aggregate(outsider,student,
   CURRENT_TIMESTAMP-interval '3 days',CURRENT_TIMESTAMP+interval '1 day');
 IF stats.completed_sessions<>0 OR stats.total_correct<>0 OR stats.total_questions<>0 THEN
   RAISE EXCEPTION 'FAIL: unrelated principal received private aggregates';
 END IF;
 SELECT * INTO stats FROM quizai_teacher_practice_aggregate(teacher,student,
   CURRENT_TIMESTAMP-interval '12 hours',CURRENT_TIMESTAMP-interval '12 hours'+interval '1 second');
 IF stats.completed_sessions<>1 THEN RAISE EXCEPTION 'FAIL: aggregate inclusive lower boundary'; END IF;
 SELECT * INTO stats FROM quizai_teacher_practice_aggregate(teacher,student,
   CURRENT_TIMESTAMP-interval '13 hours',CURRENT_TIMESTAMP-interval '12 hours');
 IF stats.completed_sessions<>0 THEN RAISE EXCEPTION 'FAIL: aggregate exclusive upper boundary'; END IF;
 RAISE NOTICE 'PASS: schema/read-model regressions (not service/concurrency acceptance); fixtures will ROLLBACK';
END $$;
ROLLBACK;
