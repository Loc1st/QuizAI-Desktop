-- Execute only on an isolated migrated TEST/DEV schema. Read-only catalog assertions.
-- This proves catalog structure, not API authorization or concurrency behavior.
DO $$
DECLARE
 schema_oid OID := current_schema()::regnamespace;
 expected_tables TEXT[] := ARRAY[
  'users','auth_sessions','classes','class_members','subjects','topics',
  'documents','document_extractions','ai_generations','question_banks','questions',
  'question_versions','question_options','quizzes','quiz_questions',
  'practice_sessions','practice_session_questions','practice_answers','ai_conversations','ai_messages',
  'assignments','assignment_recipients','assignment_question_snapshots','assignment_snapshot_options',
  'assignment_publication_events','attempts','attempt_answers','attempt_device_bindings',
  'device_transfer_requests','submission_candidates','submission_candidate_answers',
  'late_submission_reviews','submission_scores','attempt_result_events',
  'assignment_recipient_score_events','idempotency_receipts','audit_events'];
 actual_tables TEXT[]; item RECORD; object_oid OID; fk RECORD; source_columns TEXT[]; target_columns TEXT[];
BEGIN
 SELECT array_agg(c.relname::TEXT ORDER BY c.relname) INTO actual_tables
 FROM pg_catalog.pg_class c WHERE c.relnamespace=schema_oid AND c.relkind IN ('r','p')
   AND c.relname<>'flyway_schema_history';
 IF actual_tables IS DISTINCT FROM (SELECT array_agg(n ORDER BY n) FROM unnest(expected_tables) n) THEN
   RAISE EXCEPTION 'Expected exactly the approved 37 tables (plus Flyway history), found %',actual_tables;
 END IF;
 FOR item IN SELECT c.oid,c.relname FROM pg_catalog.pg_class c
   WHERE c.relnamespace=schema_oid AND c.relname=ANY(expected_tables)
 LOOP
   IF NOT EXISTS (
     SELECT 1 FROM pg_catalog.pg_constraint p JOIN pg_catalog.pg_attribute a
       ON a.attrelid=p.conrelid AND a.attnum=p.conkey[1]
     WHERE p.conrelid=item.oid AND p.contype='p' AND cardinality(p.conkey)=1
       AND a.attname='id' AND a.atttypid='uuid'::regtype AND a.attnotnull
   ) THEN RAISE EXCEPTION 'Missing UUID id primary key on %',item.relname; END IF;
 END LOOP;
 -- Inspect the real composite FK, not merely a matching name in another schema.
 SELECT c.* INTO fk FROM pg_catalog.pg_constraint c
 WHERE c.connamespace=schema_oid AND c.conrelid=format('%I.attempts',current_schema())::regclass
   AND c.conname='fk_attempt_effective_candidate_same_attempt' AND c.contype='f';
 IF NOT FOUND THEN RAISE EXCEPTION 'Missing DBX-16 composite FK'; END IF;
 SELECT array_agg(a.attname::TEXT ORDER BY k.ord) INTO source_columns
 FROM unnest(fk.conkey) WITH ORDINALITY k(num,ord)
 JOIN pg_catalog.pg_attribute a ON a.attrelid=fk.conrelid AND a.attnum=k.num;
 SELECT array_agg(a.attname::TEXT ORDER BY k.ord) INTO target_columns
 FROM unnest(fk.confkey) WITH ORDINALITY k(num,ord)
 JOIN pg_catalog.pg_attribute a ON a.attrelid=fk.confrelid AND a.attnum=k.num;
 IF source_columns<>ARRAY['id','effective_candidate_id'] OR target_columns<>ARRAY['attempt_id','id']
    OR fk.confrelid<>format('%I.submission_candidates',current_schema())::regclass
    OR fk.confdeltype<>'r' OR NOT fk.convalidated OR NOT fk.condeferrable OR fk.condeferred THEN
   RAISE EXCEPTION 'Incorrect DBX-16 definition: %',pg_catalog.pg_get_constraintdef(fk.oid);
 END IF;
 IF EXISTS (
   SELECT 1 FROM pg_catalog.pg_constraint c
   WHERE c.connamespace=schema_oid AND c.contype='f'
     AND (NOT c.convalidated OR c.confdeltype NOT IN ('a','r'))
 ) THEN RAISE EXCEPTION 'Unvalidated or cascading QuizAI foreign key'; END IF;
 FOR item IN SELECT * FROM (VALUES
   ('uq_candidate_base_per_attempt','submission_candidates','kind','CLIENT_ON_TIME','SERVER_TIMEOUT',ARRAY['attempt_id']),
   ('uq_candidate_late_per_attempt','submission_candidates','kind','CLIENT_LATE','CLIENT_LATE',ARRAY['attempt_id']),
   ('uq_attempt_bindings_active','attempt_device_bindings','revoked_at','IS NULL','IS NULL',ARRAY['attempt_id']),
   ('uq_class_members_active','class_members','left_at','IS NULL','IS NULL',ARRAY['class_id','student_id']),
   ('uq_classes_enabled_join_code_digest','classes','join_enabled','join_enabled','join_enabled',ARRAY['join_code_digest'])
 ) AS e(name,table_name,predicate_column,predicate_part1,predicate_part2,key_columns)
 LOOP
   IF NOT EXISTS (
     SELECT 1 FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class ix ON ix.oid=i.indexrelid
     JOIN pg_catalog.pg_class tab ON tab.oid=i.indrelid
     WHERE ix.relnamespace=schema_oid AND ix.relname=item.name AND tab.relname=item.table_name
       AND i.indisunique AND i.indisvalid AND i.indisready AND i.indpred IS NOT NULL
       AND position(item.predicate_column IN pg_catalog.pg_get_expr(i.indpred,i.indrelid))>0
       AND position(item.predicate_part1 IN pg_catalog.pg_get_expr(i.indpred,i.indrelid))>0
       AND position(item.predicate_part2 IN pg_catalog.pg_get_expr(i.indpred,i.indrelid))>0
       AND (SELECT array_agg(a.attname::TEXT ORDER BY k.ord)
            FROM unnest(i.indkey::SMALLINT[]) WITH ORDINALITY k(num,ord)
            JOIN pg_catalog.pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.num
            WHERE k.ord<=i.indnkeyatts)=item.key_columns
   ) THEN RAISE EXCEPTION 'Missing/incorrect critical partial unique index %',item.name; END IF;
 END LOOP;
 FOR item IN SELECT * FROM (VALUES
   ('submission_scores','ck_submission_score_ratio'),('submission_scores','ck_submission_score_bounds'),
   ('assignment_recipient_score_events','ck_recipient_old_score_pair'),
   ('assignment_recipient_score_events','ck_recipient_new_score_pair'),
   ('question_versions','ck_question_versions_approval'),
   ('practice_sessions','ck_practice_session_completion'),('assignments','ck_assignment_window')
 ) AS e(table_name,name)
 LOOP
   IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint c
     WHERE c.connamespace=schema_oid AND c.conrelid=format('%I.%I',current_schema(),item.table_name)::regclass
       AND c.conname=item.name AND c.contype='c' AND c.convalidated) THEN
     RAISE EXCEPTION 'Missing validated CHECK %',item.name;
   END IF;
 END LOOP;
 FOR item IN SELECT * FROM (VALUES
   ('submission_candidates','trg_candidates_immutable','quizai_append_only'),
   ('submission_candidate_answers','trg_candidate_answers_immutable','quizai_append_only'),
   ('submission_scores','trg_scores_immutable','quizai_append_only'),
   ('late_submission_reviews','trg_late_reviews_immutable','quizai_append_only'),
   ('attempt_result_events','trg_result_events_immutable','quizai_append_only'),
   ('assignment_recipient_score_events','trg_recipient_score_events_immutable','quizai_append_only'),
   ('audit_events','trg_audit_events_immutable','quizai_append_only'),
   ('assignment_publication_events','trg_publication_events_immutable','quizai_append_only'),
   ('ai_messages','trg_ai_messages_immutable','quizai_append_only'),
   ('assignments','trg_release_non_retraction','quizai_guard_release_non_retraction'),
   ('assignments','trg_publication_after_release','quizai_observe_assignment_publications'),
   ('attempts','trg_publication_before_attempt','quizai_observe_attempt_publications'),
   ('attempts','trg_publication_after_attempt','quizai_observe_attempt_publications'),
   ('attempts','trg_effective_candidate_eligibility','quizai_check_effective_candidate_eligibility'),
   ('submission_scores','trg_score_truth','quizai_verify_persisted_score'),
   ('submission_candidate_answers','trg_candidate_answers_no_late_inserts','quizai_candidate_answer_not_scored'),
   ('late_submission_reviews','trg_late_review_owner','quizai_check_late_review_teacher'),
   ('question_options','trg_question_option_immutable','quizai_guard_question_option'),
   ('question_versions','trg_question_version_immutable','quizai_guard_question_version'),
   ('assignment_recipients','trg_assignment_recipient_membership','quizai_guard_recipient_membership'),
   ('assignment_question_snapshots','trg_snapshot_guard','quizai_guard_published_snapshot'),
   ('assignment_snapshot_options','trg_snapshot_option_guard','quizai_guard_published_snapshot'),
   ('assignments','trg_assignment_publish','quizai_guard_assignment_publish'),
   ('class_members','trg_class_members_episode_guard','quizai_guard_membership_episode'),
   ('quiz_questions','trg_quiz_question_version_lock','quizai_lock_quiz_question_version'),
   ('practice_sessions','trg_practice_session_state','quizai_guard_practice_session'),
   ('practice_session_questions','trg_practice_question_list','quizai_guard_practice_question_list'),
   ('practice_answers','trg_practice_answer_state','quizai_guard_practice_answer_state')
 ) AS e(table_name,name,function_name)
 LOOP
   IF NOT EXISTS (
     SELECT 1 FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid
     JOIN pg_catalog.pg_proc p ON p.oid=t.tgfoid
     WHERE c.relnamespace=schema_oid AND c.relname=item.table_name AND t.tgname=item.name
       AND NOT t.tgisinternal AND t.tgenabled IN ('O','A')
       AND p.pronamespace=schema_oid AND p.proname=item.function_name
   ) THEN RAISE EXCEPTION 'Missing enabled guard %.%',item.table_name,item.name; END IF;
 END LOOP;
 FOR item IN SELECT * FROM (VALUES
   ('quizai_calculate_candidate_score(uuid)'),
   ('quizai_can_view_score(uuid,uuid,timestamp with time zone)'),
   ('quizai_can_view_answers(uuid,uuid,timestamp with time zone)'),
   ('quizai_teacher_practice_aggregate(uuid,uuid,timestamp with time zone,timestamp with time zone)'),
   ('quizai_record_current_publications(uuid)'),
   ('quizai_candidate_answer_not_scored()'),('quizai_verify_persisted_score()'),
   ('quizai_check_effective_candidate_eligibility()'),('quizai_check_late_review_teacher()')
 ) AS e(signature)
 LOOP
   object_oid:=to_regprocedure(format('%I.%s',current_schema(),item.signature));
   IF object_oid IS NULL THEN RAISE EXCEPTION 'Missing function %',item.signature; END IF;
 END LOOP;
 RAISE NOTICE 'PASS: exact 37 tables, UUID PKs, DBX-16 definition, FK deletion policy, critical indexes/CHECKs, V7/V8 guards and function signatures';
END $$;
