# TelecomOSS

Telecom Service Assurance & Trouble Ticket Management System, built with Java 8,
Spring Boot 2.7.18, PostgreSQL, explicit Spring JDBC SQL, and Thymeleaf.

Read **MILESTONES.md** first to resume development. **BUILD_PLAN.md** describes the
approved implementation sequence. Milestones 1-2 provide the schema/read layer;
milestone 3 adds secured login and OTP/reset. Milestone 4 adds customer service
listing, ticket creation/tracking and desk intake. See docs/TICKET_WORKFLOW.md.
Milestone 5 adds the desk assignment queue, ranked engineer recommendations and
atomic assignment/reassignment. See docs/ASSIGNMENT.md for the workflow and rules.
Milestone 6 adds assigned engineer work, acknowledgment, pending-customer handling,
resolution, desk closure, cancellation and customer feedback. See docs/LIFECYCLE.md.
Milestone 7 adds independent SLA views, priority changes, three-level escalation
and explicit operational resumption. See docs/SLA_MONITORING.md for rules and testing.
Milestone 8 adds notification inbox/read actions, local background delivery and
persisted network event simulation/recovery. See docs/NOTIFICATIONS_EVENTS.md.
Milestone 9 completes all role dashboards and seven asynchronous CSV/TXT reports.
Milestone 10 adds assessment evidence, scratch-only raw JDBC/Stream exercises,
synthetic exports and packaged verification. Start with **docs/DEMO.md** for the
complete setup/demo, **docs/REPORTS.md** for reporting semantics and **docs/ASSESSMENT.md**
for the requirement map. **docs/ARCHITECTURE.md** records invariants and scope limits.

## Local prerequisites

- A Java 8 **JDK**, Maven 3.9.x, and PostgreSQL. PostgreSQL 18 is the current local server.
- Create a dedicated development database and a login allowed to create its schema.
- Run commands from the repository root.

The PowerShell Maven launcher selects Java 8 from `TELECOM_JAVA_HOME`, a valid
`JAVA_HOME`, or common installed-JDK directories. It restores JAVA_HOME afterward
and never changes system settings. For another installation, set:

```powershell
$env:TELECOM_JAVA_HOME = 'C:\path\to\jdk8'
.\scripts\mvn.ps1 -version
```

In IntelliJ, use the shared **TelecomOSS Dev** run configuration (in `.run/`). It
starts the application with `--spring.profiles.active=dev` and the project root as
its working directory, displaying the session-bound development OTP on step 2. Stop any
existing application instance before starting it. Without the dev profile, OTP
delivery is unavailable until a real delivery adapter is configured.

In IntelliJ, select Java 8 for the project SDK and Maven runner/importer. Standard
`mvn` remains usable once JAVA_HOME points to the JDK directory, not `bin` or a JRE.

## Database configuration

Copy `config/application-local.properties.example` to
`config/application-local.properties` and enter your datasource settings. The local
file is ignored by Git, imported by Spring Boot, and kept outside the application JAR.
Existing settings on this workstation have already been preserved in that file.

Alternatively, supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` environment variables. Environment values take precedence
over the local file. Do not put passwords in command-line arguments or documentation.

## Initial schema installation

Only for an **empty** dedicated development database:

```powershell
.\scripts\Initialize-Database.ps1 -Seed
```

Omit `-Seed` to install schema without demo rows. The command uses Spring's datasource
configuration, refuses existing public-schema objects, and installs the baseline,
optional seed, and migration ledger in one transaction. It never drops existing data.
Normal application startup does not create or modify schema. See `db/README.md`
for SQL details and the fixed reference time for seeded SLA demonstrations.

## Run and verify

For this workstation's already provisioned database, apply pending migrations with
`scripts/Update-Database.ps1` without `-DevAccounts`. For a freshly installed V001
database only, add `-DevAccounts` once to create the four demo logins while upgrading.
The runner currently supports V001 through V008. Initial random passwords are in
the ignored `config/dev-accounts.properties` file. Open `/login` after restarting
the app with the dev profile. See `docs/AUTHENTICATION.md` for the complete flow.

```powershell
.\scripts\mvn.ps1 test
.\scripts\mvn.ps1 '-Dtelecom.integration=true' '-Dtest=DatabaseSchemaIntegrationTest' test
.\scripts\mvn.ps1 spring-boot:run '-Dspring-boot.run.profiles=dev'
```

The ordinary tests do not require PostgreSQL. The opt-in schema integration test uses
a read-only connection to the configured database and checks the baseline tables and
mutation guards. A dev-profile startup logs `Database connectivity check: SUCCESS`
after SELECT 1. The customer and desk workspaces provide service listing and ticket
intake/tracking, desk assignment and the manual lifecycle; engineer work is available.
Desk and manager dashboards link to SLA monitoring. The monitor checks active tickets
60 seconds after startup and every 60 seconds thereafter; fixed-date seed tickets
may already be overdue. It records escalation/history and pending notifications.
Dashboard links now open recipient inboxes or the desk processing overview. In-app
notification processing runs every five seconds. Desk can configure network nodes,
map existing service codes, and simulate events in the dev profile. Persisted pending
events recover on startup and every 15 seconds. No external email/SMS is sent.
There is no public health endpoint.

For the DAO integration tests in a separate temporary PostgreSQL instance, run:

```powershell
.\scripts\Test-DaoIntegration.ps1
```

The harness creates and removes its own test cluster; it does not load application
database settings. See `docs/DATA_ACCESS.md` for DAO contracts, paging, mapping and
test prerequisites.

For the full authentication/security suite plus DAO tests in separate disposable
databases, run `.\scripts\Test-AuthIntegration.ps1`.

Database setup errors are summarized without connection credentials. For local
administration, diagnose server availability and login permissions in your database
client; do not paste credentials or raw connection diagnostics into shared reports.

## Handoff policy

After every milestone, update MILESTONES.md with implementation details, tests,
actual migration state, known limitations, and the exact next task. Record partial
progress when pausing. A script existing on disk does not mean it was applied.
