# Initial PostgreSQL schema

This folder contains the approved six-entity baseline and additive application migrations.
It uses explicit SQL and requires no JPA/Hibernate.

## Apply

The preferred setup command from the project root is:

```powershell
.\scripts\Initialize-Database.ps1 -Seed
```

It uses the application's existing datasource settings, refuses a non-empty schema,
and installs the baseline, optional fixtures and checksum ledger in one transaction.
Omit `-Seed` for schema only. See the root README for local configuration.

For manual psql administration, create a dedicated empty development database using your PostgreSQL administration tools.
From the project root, run the following with an existing PostgreSQL login. Replace
`your_user` and `telecom_oss_dev` as needed. Let psql prompt for a password; do not commit it.

```powershell
psql -X -h localhost -U your_user -d telecom_oss_dev -v ON_ERROR_STOP=1 -f db/schema.sql
psql -X -h localhost -U your_user -d telecom_oss_dev -v ON_ERROR_STOP=1 -f db/seed-dev.sql
psql -X -h localhost -U your_user -d telecom_oss_dev -v ON_ERROR_STOP=1 -f db/verify-dev.sql
```

`schema.sql` includes the canonical `migrations/V001__initial_schema.sql`. The manual
psql route does not install the technical migration ledger; record a verified adoption
before applying future migrations. Run each script once. Both scripts are transactional and deliberately fail on existing
objects or duplicate business keys. They do not drop, truncate, or reset existing data.
The development seed is optional and must not be applied to a production database.
Datasource settings are imported from an ignored external local file or supplied by
environment variables. Automatic Spring SQL initialization is disabled.
`verify-dev.sql` checks fixture counts, history continuity, SLA scenarios, key/enum
constraints and append-only guards. It ends with ROLLBACK and is intended only for
the unchanged seeded development dataset.

## Verify application connectivity locally

Additive migrations through V008 (including case-study resolution-code compatibility) are applied with
`scripts/Update-Database.ps1`; do not rerun V001/seed on the populated database.
See docs/AUTHENTICATION.md for optional development account provisioning.

With PostgreSQL running and the existing `application.properties` datasource settings
configured, start the application with the development profile from the project root:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Alternatively, add `--spring.profiles.active=dev` to the application arguments in
your IDE run configuration, then restart the application. No datasource changes
are required. Maven needs `JAVA_HOME` to point to a JDK installation.

The development-only startup check executes `SELECT 1` through the existing
`JdbcTemplate` and logs one of these results:

```text
Database connectivity check: SUCCESS
Database connectivity check: FAILURE (check local datasource configuration and database availability)
Database connectivity check: FAILURE (unexpected verification result)
```

An unavailable database is reported without aborting startup from this check.
The check does not log the exception, connection URL, username or password, expose
an HTTP endpoint, or change schema/data. It is disabled without the `dev` profile.
Other framework components can still emit their own diagnostic logs or fail startup.
A success verifies basic connectivity only, not the existence of application tables.

Run the focused tests without a live database:

```powershell
mvn "-Dtest=DatabaseConnectivityCheckTest" test
```

The tests cover success, a connection exception without detail leakage, an unexpected
query result, and development-profile activation/deactivation.

## Model and policies

- A customer owns services; a service has tickets. Ticket customer ownership is derived
  through the service. Service ownership transfer is outside this initial scope.
- A ticket optionally references one engineer. Workload is derived from unresolved,
  non-cancelled assigned tickets; it is not cached on the engineer.
- One specialization per engineer; region on a service supports assignment matching.
- Ticket priority and SLA policy version form a composite foreign key. New policies
  are inserted with a new version rather than editing old rows. New tickets select
  the latest version for their priority in the JDBC service.
- Response and resolution clocks start at ticket creation and run continuously.
  First engineer acknowledgment records `first_response_at`. Waiting for a customer
  does not pause timing. The policy's warning threshold defaults to 80% elapsed.
- JDBC code must calculate deadlines from the selected policy. A priority change
  recalculates them from original creation time, without restarting the clock.
- SLA state and remaining duration are calculated at query/service time. Completed
  targets are assessed using their completion timestamp, not the current time.
- Reopening resolved tickets is unsupported. V002-V008 add authentication,
  notification/audit, assignment configuration, immutable lifecycle metadata and
  escalation history/state/recipients, local delivery receipts and network events.
  V008 adds the eight case-study resolution codes while retaining the five historical
  values. See docs/SLA_MONITORING.md and docs/NOTIFICATIONS_EVENTS.md for current rules.
- Native enum values are case-sensitive. Use explicit JDBC enum conversion/casts.
  Do not use PostgreSQL enum ordering to rank urgency; use a comparator or SQL CASE.
- `changed_by` is a stable external actor reference until a user table is designed.
- History and SLA policy rows reject UPDATE/DELETE through triggers. The future
  application role must not own the schema or have TRUNCATE/DDL privileges.
- Every ticket creation/status change must insert history in the same JDBC
  transaction. The schema does not automatically create history or enforce the
  complete transition graph, cross-row history continuity, or deadline calculations.
- JDBC updates explicitly maintain `updated_at` and increment `row_version`, using
  the previous version in the UPDATE predicate to detect concurrent edits.
- Email/mobile are required but not unique. Customer status, service status, and
  severity use the approved initial controlled values.

## Seed dataset

Expected counts: 3 customers, 5 services, 4 engineers, 4 SLA policies,
8 tickets, and 25 status-history entries. Every service type, customer type,
ticket priority, and ticket status is represented. Contact values are fictional.

Evaluate fixture SLA scenarios at **2026-10-15 12:00 UTC / 17:30 Asia/Kolkata**.
The dates are intentionally fixed so demonstrations are repeatable; using the live
clock later will produce different SLA classifications.

| Ticket suffix | Scenario at reference time |
|---|---|
| 000001 | OPEN, critical response at risk |
| 000002 | ASSIGNED, high response and resolution breached |
| 000003 | IN_PROGRESS, medium response met and resolution at risk |
| 000004 | PENDING_CUSTOMER, low SLA still running within target |
| 000005 | ESCALATED, critical response met and resolution breached |
| 000006 | RESOLVED, high response and resolution met |
| 000007 | CLOSED, low response and resolution met |
| 000008 | CANCELLED before assignment |

The seed inserts each complete history path in the same transaction as its tickets.
Business identifiers are joined rather than assuming generated numeric IDs.
