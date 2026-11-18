-- MANUAL DEV/TEST ONLY. Run AFTER a separately authorized Flyway V1 migration.
-- No production. Use psql -X -v ON_ERROR_STOP=1 -f P2A_ROLLBACK_CONTRACT_TEST.sql
-- All seeded rows are rolled back at end. On any unexpected result, psql exits non-zero.
BEGIN;

DO $quizai_test$
DECLARE
    t UUID := '00000000-0000-4000-8000-000000000101';
    s UUID := '00000000-0000-4000-8000-000000000102';
    c UUID := '00000000-0000-4000-8000-000000000103';
    d UUID := '00000000-0000-4000-8000-000000000104';
    m UUID := '00000000-0000-4000-8000-000000000105';
    p UUID := '00000000-0000-4000-8000-000000000106';
    q UUID := '00000000-0000-4000-8000-000000000107';
BEGIN
    INSERT INTO users(id,email,password_hash,role,status) VALUES
    (t, 'p2a-teacher-qa@example.invalid', 'hash-not-plaintext', 'TEACHER', 'ACTIVE'),
    (s, 'p2a-student-qa@example.invalid', 'hash-not-plaintext', 'STUDENT', 'ACTIVE');

    BEGIN
        INSERT INTO users(id,email,password_hash,role,status)
        VALUES ('00000000-0000-4000-8000-000000000108','P2A-STUDENT-QA@example.invalid','hash','STUDENT','ACTIVE');
        RAISE EXCEPTION 'FAIL email normalization check did not reject uppercase';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO users(id,email,password_hash,role,status)
        VALUES ('00000000-0000-4000-8000-000000000109','p2a-student-qa@example.invalid','hash','STUDENT','ACTIVE');
        RAISE EXCEPTION 'FAIL duplicate email did not reject';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    INSERT INTO classes(id,teacher_id,name,join_code_digest,join_enabled,status)
    VALUES (c,t,'QA class 1','qa-hmac-digest',TRUE,'ACTIVE'),
           (d,t,'QA class 2','qa-hmac-digest',FALSE,'ACTIVE');
    BEGIN
        UPDATE classes SET join_enabled=TRUE WHERE id=d;
        RAISE EXCEPTION 'FAIL enabled join digest uniqueness missing';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    UPDATE classes SET join_code_digest='another-qa-hmac-digest', join_enabled=TRUE WHERE id=d;

    INSERT INTO class_members(id,class_id,student_id,joined_at)
    VALUES (m,c,s,CURRENT_TIMESTAMP);
    BEGIN
        INSERT INTO class_members(id,class_id,student_id,joined_at)
        VALUES ('00000000-0000-4000-8000-000000000110',c,s,CURRENT_TIMESTAMP);
        RAISE EXCEPTION 'FAIL two active membership episodes accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    UPDATE class_members SET left_at=joined_at+INTERVAL '1 second' WHERE id=m;
    INSERT INTO class_members(id,class_id,student_id,joined_at)
    VALUES ('00000000-0000-4000-8000-000000000111',c,s,CURRENT_TIMESTAMP+INTERVAL '2 seconds');
    BEGIN
        UPDATE class_members SET left_at=NULL WHERE id=m;
        RAISE EXCEPTION 'FAIL closed membership episode reopened';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO subjects(id,code,name,active,catalog_version)
    VALUES (p,'P2A_QA_SUB','QA Subject',TRUE,1);
    BEGIN
        INSERT INTO subjects(id,code,name,active,catalog_version)
        VALUES ('00000000-0000-4000-8000-000000000112','P2A_QA_SUB','Duplicate',TRUE,1);
        RAISE EXCEPTION 'FAIL duplicate subject code accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    INSERT INTO topics(id,subject_id,code,name,active)
    VALUES (q,p,'P2A_QA_TOPIC','Topic',TRUE);
    BEGIN
        INSERT INTO topics(id,subject_id,code,name,active)
        VALUES ('00000000-0000-4000-8000-000000000113',p,'P2A_QA_TOPIC','Duplicate',TRUE);
        RAISE EXCEPTION 'FAIL duplicate topic code accepted within subject';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO auth_sessions(id,user_id,session_token_hash,device_session_id,created_at,expires_at)
        VALUES ('00000000-0000-4000-8000-000000000114',s,'test-token-hash','test-device',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
        RAISE EXCEPTION 'FAIL invalid session expiry accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    RAISE NOTICE 'P2-A contract tests passed (all seeded rows are about to roll back)';
END;
$quizai_test$;

ROLLBACK;
