#!/usr/bin/env python3
"""No-PostgreSQL, no-network contract validator for offline P2-A code package."""
from pathlib import Path
import hashlib
import re
import sys

P = Path(__file__).resolve().parent.parent
sql = (P/'quizai-server/src/main/resources/db/migration/V1__identity_classes_catalog.sql').read_text(encoding='utf-8')
test = (P/'tests/P2A_ROLLBACK_CONTRACT_TEST.sql').read_text(encoding='utf-8')
expected = {'users', 'auth_sessions', 'classes','class_members','subjects','topics'}
actual = set(re.findall(r'^CREATE TABLE\s+([a-z_]+)\s*\(', sql, re.MULTILINE|re.IGNORECASE))
assert actual == expected, (actual, expected)
assert len(re.findall(r'^CREATE TABLE\s+',sql,re.MULTILINE)) == 6
assert re.search(r'CREATE UNIQUE INDEX uq_classes_enabled_join_code_digest\s+ON classes \(join_code_digest\) WHERE join_enabled IS TRUE;',sql)
assert re.search(r'CREATE UNIQUE INDEX uq_class_members_active\s+ON class_members \(class_id, student_id\) WHERE left_at IS NULL;',sql)
assert 'CONSTRAINT ck_users_email_normalized' in sql
assert 'CONSTRAINT uq_users_email UNIQUE (email)' in sql
assert "role IN ('STUDENT', 'TEACHER')" in sql
assert 'uq_topics_subject_code UNIQUE (subject_id, code)' in sql
assert 'trg_class_members_episode_guard' in sql
assert 'ON DELETE CASCADE' not in sql.upper()
sql_statements = '\n'.join(line for line in sql.splitlines() if not line.lstrip().startswith('--'))
assert not re.search(r'\bCREATE\s+(DATABASE|ROLE|USER|EXTENSION)\b',sql_statements,re.IGNORECASE)
assert not re.search(r'\bDROP\s+(TABLE|DATABASE|SCHEMA)\b',sql_statements,re.IGNORECASE)
assert test.lstrip().startswith('-- MANUAL DEV/TEST ONLY.')
assert re.search(r'^BEGIN;',test,re.MULTILINE)
assert re.search(r'^ROLLBACK;',test,re.MULTILINE)
assert 'DO $quizai_test$' in test
for x in ('join digest uniqueness','two active membership','closed membership episode','duplicate subject code','duplicate topic code','invalid session expiry'):
    assert x in test
assert all(x not in sql.lower() for x in ('assignment_recipients', 'ai_conversations','attempts', 'migration p2-b'))
root=Path('/mnt/data/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md')
if root.exists():
    assert hashlib.sha256(root.read_bytes()).hexdigest() == '5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab'
print('PASS: 6/6 approved P2-A tables, partial uniqueness, safe foreign keys, immutable episodes, ROLLBACK contract test')
print('NOT RUN: PostgreSQL parser, Flyway, live constraints, DEV/TEST database, application build')
