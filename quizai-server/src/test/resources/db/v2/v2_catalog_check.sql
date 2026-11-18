-- Run READ ONLY on quizai_v2_test or quizai_v2_dev after applying Flyway v2.
-- In psql, use -v ON_ERROR_STOP=1. Never run against v1 databases.
DO $$
DECLARE
  expected text[] := ARRAY[
    'ai_conversations','ai_messages','assignment_questions','assignment_recipients',
    'assignments','attempt_answers','attempts','audit_events','auth_sessions',
    'class_members','classes','documents','practice_answers','practice_session_questions',
    'practice_sessions','question_banks','question_options','question_versions',
    'questions','quiz_questions','quizzes','subjects','topics','users'
  ];
  actual text[];
  migration_count integer;
  versions text[]; scripts text[]; checksums integer[]; ranks integer[]; valid boolean;
  uuid_primary_keys integer;
  expected_checksums integer[] := ARRAY[-687492251,2042360829,-1945806434,-970986061,-1233916456];
BEGIN
  IF current_database() NOT IN ('quizai_v2_test','quizai_v2_dev') THEN
      RAISE EXCEPTION 'Wrong DB: %, expected quizai_v2_test/dev only', current_database();
  END IF;
  SELECT array_agg(tablename ORDER BY tablename) INTO actual
    FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history';
  IF actual IS DISTINCT FROM expected THEN
    RAISE EXCEPTION '24 business table contract mismatch: found %', actual;
  END IF;
  SELECT count(*),array_agg(version ORDER BY installed_rank),array_agg(script ORDER BY installed_rank),
         array_agg(checksum ORDER BY installed_rank),array_agg(installed_rank ORDER BY installed_rank),
         bool_and(success AND type='SQL' AND checksum IS NOT NULL)
    INTO migration_count,versions,scripts,checksums,ranks,valid FROM public.flyway_schema_history;
  IF migration_count<>5 OR valid IS NOT TRUE OR versions IS DISTINCT FROM ARRAY['1','2','3','4','5']
    OR ranks IS DISTINCT FROM ARRAY[1,2,3,4,5]
    OR scripts IS DISTINCT FROM ARRAY['V1__v2_identity_class_catalog.sql','V2__v2_documents_questions_quizzes.sql','V3__v2_practice_and_ai.sql','V4__v2_assignments_attempts_audit.sql','V5__v2_integrity_guards.sql']
    OR checksums IS DISTINCT FROM expected_checksums THEN
      RAISE EXCEPTION 'V2 versions/scripts/checksums/history mismatch';
  END IF;
  SELECT count(*) INTO uuid_primary_keys FROM pg_constraint k
    JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
    JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=k.conkey[1]
    WHERE n.nspname='public' AND c.relname<>'flyway_schema_history' AND k.contype='p'
      AND cardinality(k.conkey)=1 AND a.attname='id' AND a.atttypid='uuid'::regtype;
  IF uuid_primary_keys<>24 THEN RAISE EXCEPTION 'Expected exactly 24 UUID primary keys'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
    AND table_name<>'flyway_schema_history' AND column_name LIKE '%\_at' ESCAPE '\'
    AND data_type<>'timestamp with time zone') THEN
      RAISE EXCEPTION 'Business event timestamps must be TIMESTAMPTZ';
  END IF;
  RAISE NOTICE 'V2 catalog PASS: 24 business tables + 5 Flyway migrations on %', current_database();
END $$;
