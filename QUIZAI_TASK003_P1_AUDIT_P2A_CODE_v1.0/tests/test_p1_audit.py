"""Unit tests of static P1 auditor only; no database access."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

S = Path(__file__).resolve().parents[1]/'scripts/p1_final_audit.py'
spec = importlib.util.spec_from_file_location('p1_final_audit',S)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class P1AuditTests(unittest.TestCase):
    def test_missing_repo_files_fail(self):
        with tempfile.TemporaryDirectory() as folder:
            checks, warnings, errors = audit.report(Path(folder))
            self.assertTrue(errors)
            self.assertIn('ERD approved file missing', errors)

    def test_repository_fixture(self):
        candidate = Path('/mnt/data/_test_quizai_p1')
        if not candidate.is_dir():
            self.skipTest('No local synthetic P1 repo; never use user project source')
        _, warnings, errors = audit.report(candidate)
        self.assertFalse(errors, errors)

    def test_reject_wrong_db_env(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            gradle=root/'quizai-server/build.gradle'
            gradle.parent.mkdir(parents=True)
            gradle.write_text('spring-boot-starter-jdbc spring-boot-starter-flyway org.postgresql:postgresql flyway-database-postgresql')
            (root/'settings.gradle').write_text('include quizai-server')
            docs=root/'docs'; docs.mkdir()
            original=Path('/mnt/data/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md')
            if original.exists():
                (docs/original.name).write_bytes(original.read_bytes())
            base=root/'quizai-server/src/main/resources/application.yml'
            base.parent.mkdir(parents=True)
            base.write_text('''spring:\n  flyway:\n    enabled: false\n  sql:\n    init:\n      mode: never\n  jpa:\n    hibernate:\n      ddl-auto: none\n  autoconfigure:\n    exclude:\n      - org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration\n      - org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration\n''')
            dev=base.parent/'application-dev.yml'
            dev.write_text('''spring:\n  datasource:\n    url: ${QUIZAI_TEST_DB_URL}\n    username: ${QUIZAI_TEST_DB_USER}\n    password: ${QUIZAI_TEST_DB_PASSWORD}\n  flyway:\n    enabled: false\n''')
            tst=root/'quizai-server/src/test/resources/application-test.yml'
            tst.parent.mkdir(parents=True)
            tst.write_text('''spring:\n  datasource:\n    url: ${QUIZAI_TEST_DB_URL}\n    username: ${QUIZAI_TEST_DB_USER}\n    password: ${QUIZAI_TEST_DB_PASSWORD}\n  flyway:\n    enabled: false\n''')
            _,_,errors=audit.report(root)
            self.assertTrue(any('wrong DB environment' in e for e in errors))

if __name__ == '__main__':
    unittest.main()
