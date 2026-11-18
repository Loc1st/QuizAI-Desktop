# PostgreSQL 18 local TEST then DEV

Only the existing local PostgreSQL instance at port 5432 and the dedicated
`quizai_test` / `quizai_dev` databases and owner logins are authorized. No other
database, PostgreSQL server, Docker instance or production environment is used.

Run the following from the PowerShell session in which the six variables are
already loaded. Process environment is local to a process and its children;
setting it in one PowerShell tab does not set it in the IDE/Codex or another tab.
Do not paste passwords into chat, URLs, scripts or command arguments.

Required names are `QUIZAI_TEST_DB_URL`, `QUIZAI_TEST_DB_USERNAME`,
`QUIZAI_TEST_DB_PASSWORD`, `QUIZAI_DEV_DB_URL`, `QUIZAI_DEV_DB_USERNAME`,
`QUIZAI_DEV_DB_PASSWORD`. URLs must explicitly identify loopback, port 5432 and
the corresponding database. The operator tool refuses query options, URL
credentials, remote hosts, a different port/database, or a different login.

From the repository root, with `JAVA_HOME` already pointing to JDK 21:

```powershell
.\quizai-server\Invoke-LocalDatabaseDeployment.ps1 -Action inspect
.\quizai-server\Invoke-LocalDatabaseDeployment.ps1 -Action deploy
```

If necessary, append `-JavaHome 'C:\path\to\installed\jdk-21'` using the actual
installed JDK 21 path. No password is requested or printed by the script.

`inspect` performs read-only database checks only. `deploy` uses the following
sequence, stopping on the first failure:

1. Resolve and validate both environments and the approved ERD checksum before
   opening any connection; require exactly migration versions V1 through V8.
   Copy migrations and acceptance SQL to one unique build snapshot for both
   environments; reject any source/snapshot checksum drift before further work.
2. Inspect TEST: actual database, PostgreSQL major version 18, port, current
   login/owner, non-superuser/non-CREATEDB/non-CREATEROLE, unknown schemas/tables/
   functions/views and existing business data. Unknown data or schema stops work.
3. If a known Flyway history exists, validate source checksums without repair.
   A changed/applied checksum or unknown/failed version stops work. No migration
   file is rewritten to make a history validation pass.
4. Apply TEST migrations through Flyway, with clean disabled and no baseline,
   schema creation, repair or database creation. Validate V1-V8 history.
5. Execute the catalog verifier and SQL transactional acceptance, including
   DBX-16. Acceptance runs on TEST only and always rolls back its fixtures, even
   when an assertion fails. It is not an assertion that all T01-T30 API/service
   tests have been implemented.
6. Only after TEST passes, inspect and migrate DEV with the same checks, then
   verify its catalog. No acceptance fixtures are inserted into DEV.

Successful execution writes non-secret evidence to
`quizai-server/build/database-deployment/verified.properties`: timestamp,
database names, version inventory and SHA-256 of each source migration. The
console also lists applied Flyway versions/checksums. A previous report is not
evidence for a later failed run: check its timestamp and current source hashes.

Migrations run in Flyway's normal PostgreSQL transactions per migration. A later
failure can leave earlier migrations applied; never delete data/schema/history
to retry. Investigate the error and fix only source that has not been applied.
The tool permits a validated, known partial history only if business data is
still empty. Existing business data always stops this operator workflow.

The files in the external delivery bundle are preserved. V1 remains byte-identical.
The integrated later migrations and acceptance tests include review corrections
to approved invariants; their checksums can intentionally differ from the source
bundle. No successful database run has been claimed merely because these files
compile or pass static checks.

## Application connection

The existing `application-dev.yml` consumes the three DEV variables; TEST uses
its own three variables on the test classpath. Default startup still excludes
datasource and Flyway. Both profiles still exclude Flyway and inherit SQL init
`never` / Hibernate `none`. This explicit Gradle operator task invokes Flyway
directly and does not enable application auto-migration or change the P1 files.

After database verification succeeds, a separate application launch from the
same environment can use `:quizai-server:bootRun --args='--spring.profiles.active=dev'`.
This selects DEV datasource configuration; it does not establish that API,
authentication, service, JavaFX or AI integration has been implemented or tested.
Those results must be verified and reported separately.
