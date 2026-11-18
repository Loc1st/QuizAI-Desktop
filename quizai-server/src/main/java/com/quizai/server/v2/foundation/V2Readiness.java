package com.quizai.server.v2.foundation;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Set;

/** Read-only startup verification of the already deployed schema. No migrate/repair/clean calls. */
final class V2Readiness {
    static final Set<String> TABLES = Set.of("users", "auth_sessions", "classes", "class_members", "subjects", "topics",
            "documents", "question_banks", "questions", "question_versions", "question_options", "quizzes", "quiz_questions",
            "practice_sessions", "practice_session_questions", "practice_answers", "ai_conversations", "ai_messages",
            "assignments", "assignment_questions", "assignment_recipients", "attempts", "attempt_answers", "audit_events");

    private V2Readiness() { }

    static void verify(DataSource source, String database) {
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            var readOnly = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(readOnly);
            Boolean identity = jdbc.queryForObject("""
                    SELECT current_database()=? AND current_user=? AND session_user=current_user
                      AND pg_get_userbyid(d.datdba)=current_user AND current_schema()='public'
                      AND current_setting('server_version_num')::int / 10000 = 18
                      AND (inet_server_addr() <<= '127.0.0.0/8'::inet OR inet_server_addr()='::1'::inet) AND inet_server_port()=5432
                      AND NOT r.rolsuper AND NOT r.rolcreatedb AND NOT r.rolcreaterole AND NOT r.rolreplication AND NOT r.rolbypassrls
                    FROM pg_database d JOIN pg_roles r ON r.rolname=current_user WHERE d.datname=current_database()
                    """, Boolean.class, database, database);
            Set<String> tables = Set.copyOf(jdbc.queryForList("""
                    SELECT table_name FROM information_schema.tables WHERE table_schema='public'
                    AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'
                    """, String.class));
            Integer foreignOwners = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname='public' AND pg_get_userbyid(c.relowner)<>current_user AND c.relkind IN ('r','v','m','S','f')
                    """, Integer.class);
            Integer unexpectedSchemas = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_namespace WHERE nspname NOT IN ('public','information_schema') AND nspname NOT LIKE 'pg_%'
                    """, Integer.class);
            Boolean schemaOwner = jdbc.queryForObject("SELECT pg_get_userbyid(nspowner) IN ('pg_database_owner',current_user) FROM pg_namespace WHERE nspname='public'", Boolean.class);
            Boolean integrityObjects = jdbc.queryForObject("""
                    WITH counts AS (
                      SELECT count(*) FILTER (WHERE c.contype='p') AS pk,
                             count(*) FILTER (WHERE c.contype='f') AS fk,
                             count(*) FILTER (WHERE c.contype='u') AS uq,
                             count(*) FILTER (WHERE c.contype='c') AS ck
                      FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                      WHERE n.nspname='public' AND t.relname<>'flyway_schema_history'
                    ) SELECT pk=24 AND fk=42 AND uq=28 AND ck=62
                      AND (SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                           WHERE n.nspname='public' AND NOT t.tgisinternal AND t.tgenabled='O')=73
                      AND (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                           WHERE n.nspname='public' AND pg_get_userbyid(p.proowner)=current_user)=28 FROM counts
                    """, Boolean.class);
            Integer history = jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history", Integer.class);
            Set<String> versions = Set.copyOf(jdbc.queryForList("SELECT version FROM public.flyway_schema_history WHERE success AND type='SQL'", String.class));
            if (!Boolean.TRUE.equals(identity) || !Boolean.TRUE.equals(schemaOwner) || !Boolean.TRUE.equals(integrityObjects)
                    || !TABLES.equals(tables) || !Integer.valueOf(0).equals(foreignOwners)
                    || !Integer.valueOf(0).equals(unexpectedSchemas) || !Integer.valueOf(5).equals(history)
                    || !Set.of("1", "2", "3", "4", "5").equals(versions)) {
                throw new IllegalStateException("V2 identity/schema mismatch");
            }
            var validation = Flyway.configure().dataSource(readOnly).locations("classpath:db/migration-v2")
                    .defaultSchema("public").createSchemas(false).cleanDisabled(true).baselineOnMigrate(false).load().validateWithResult();
            if (!validation.validationSuccessful || validation.validateCount != 5) throw new IllegalStateException("V2 checksum mismatch");
        } catch (SQLException failure) { throw new IllegalStateException("V2 read-only verification unavailable"); }
    }
}
