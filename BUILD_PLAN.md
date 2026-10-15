# TelecomOSS implementation plan

Approved sequence, updated 15 October 2026. Read MILESTONES.md for actual implementation
and database state; this plan describes the intended work, not a completion claim.

## Architecture and fixed choices

Java 8, Spring Boot 2.7.18, Maven, PostgreSQL, explicit SQL through JdbcTemplate,
Thymeleaf and Bootstrap. No JPA/Hibernate or separate microservices. Request flow:
controller -> service -> DAO -> PostgreSQL. Controllers bind forms and navigate;
services enforce permissions, business rules and transaction boundaries; DAOs own
parameterized SQL and RowMappers. Use Spring-managed transactions with the same
transaction-bound connection. Include a separate raw-JDBC assessment demonstration.

Packages grow as needed: config, model, dto, dao/impl, service/impl, controller,
security, exception, validation, scheduler, report and util. A plain tool package
contains the explicit one-time database setup main class; it is not a startup bean.

## Current foundation

Boot starts and connects to the configured PostgreSQL database. A dev-only SELECT 1
runner has five tests. Six business tables, seed fixtures and SQL verification were
created and validated previously in a temporary PostgreSQL instance. The approved
baseline is versioned as V001. Actual installation is recorded in MILESTONES.md.
Milestones 1-2 now include models, read DAOs, paging and an injectable Clock. Milestone 3
adds authentication, a V002 migration, role authorization and Bootstrap workspace
shells. Milestone 4 now adds V003, customer/desk ticket intake and tracking, independent
SLA deadlines and atomic history/notification/audit writes. Actual completion/test
evidence and database state are recorded in MILESTONES.md. Milestone 5 adds V004,
the desk queue, ranked eligible engineers and atomic assignment/reassignment with
optimistic versions. Milestone 6 adds V005, the central manual transition policy,
engineer work, acknowledgment/pending/resolution, desk closure, cancellation and
one-time customer feedback. Milestone 7 adds V006, independent SLA evaluation, automatic
escalation, desk priority changes/resumption and manager SLA views. Milestone 8 adds
V007, recipient inboxes, local background delivery/retries and persisted network event
simulation/recovery. Milestone 9 completes the four dashboards and seven asynchronous
reports. Milestone 10 adds assessment evidence, scratch JDBC/Streams, V008 resolution
codes and final package/rehearsal verification. All ten milestones are complete; read
MILESTONES.md for actual test/migration state and docs/DEMO.md for the walkthrough.

## Milestone 1 - Reproducible development baseline

1. Inspect the configured database without changing it; install baseline and optional
   development fixtures only if the schema is empty. Refuse partial/occupied schemas.
2. Introduce versioned SQL, a checksum ledger and explicit one-time installation;
   never migrate automatically on application startup.
3. Use a project-local Java 8 Maven launcher and document IDE SDK/Maven settings.
4. Preserve local datasource settings outside the application JAR; ignore the private
   file in Git, with environment overrides and a credential-free example.
5. Add read-only opt-in schema inspection, setup refusal/rollback tests, and README.
6. Initialize Git and commit the reviewed baseline using existing Git identity if set.

Acceptance: normal tests pass on Java 8; live schema inspection succeeds; repeat
installation refuses without mutation; local secrets/build/IDE files are excluded.

## Milestone 2 - Models and JDBC DAOs

1. Add Java enums matching database controls, six models and an injectable Clock.
2. Add DAO interfaces/implementations and reusable RowMappers for six entities.
3. Implement customer/service/engineer lookup, ticket/detail/history retrieval and
   latest priority-policy lookup, with Optional for missing single records.
4. Use explicit PostgreSQL enum casts, Java 8 time mapping, bounded list queries and
   a separate disposable PostgreSQL integration database for tests that write data.

Acceptance: all six seeded entities are retrieved and mapped correctly; missing IDs
produce empty results; history ordering and nullable timestamps/engineers work.

## Milestone 3 - Authentication and shared UI

1. Add users/roles/user-role, login-history and OTP-challenge migrations; link customer
   and engineer profiles without rewriting immutable historic actor references.
2. Add Spring Security sessions, password hashing, CSRF and server-side role checks.
3. Add CAPTCHA, login OTP, reset OTP and locking: five failed login attempts lock for
   15 minutes; OTPs expire after five minutes, allow five attempts and are single-use.
4. Implement a session-bound, dev-only OTP inbox through a replaceable delivery
   interface. Do not write OTPs to general logs. Real email is outside the first version.
5. Add Bootstrap layout, navigation, login/reset/error pages, four dashboard shells
   and dev accounts for Customer, Service Desk, Network Engineer and Network Manager.

Acceptance: four roles reach correct dashboards; cross-role access, stale/replayed OTP,
wrong CAPTCHA and locked accounts are rejected safely.

## Milestone 4 - Customer ticket creation/tracking

1. Add notification and audit tables before introducing transactional ticket writes.
2. Add SLAService selecting latest immutable policy version and both deadlines.
3. Add own-service listing, create ticket, own-ticket list/detail/history and service
   desk creation on behalf of a customer. Check ownership on the server.
4. Create ticket, initial history, notification and audit in one transaction; generate
   display numbers with a sequence (gaps allowed), never MAX + 1.
5. Add pagination and priority/status filters.

Acceptance: persisted authorized creation survives restart with correct deadlines,
initial history and notification; another customer's service/ticket is inaccessible.

## Milestone 5 - Assignment and reassignment

1. Add desk queue and category-to-specialization configuration.
2. Recommend up to three available engineers matching specialization and service
   region, ordered by active workload, descending experience, then employee code.
   Use Streams, comparators and Optional; show no-match explicitly.
3. Validate/assign in one transaction including status history, notification and audit.
   Use row_version to reject competing edits; recheck engineer eligibility inside it.
4. Reassignment preserves operational status; if status is unchanged record engineer
   changes in audit, rather than making an invalid same-status history entry.

Acceptance: recommendations match rules; competing assignments conflict cleanly;
any later history/notification/audit failure rolls back the whole operation.

## Milestone 6 - Complete manual lifecycle (functional MVP)

1. Central transition policy: OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED;
   IN_PROGRESS <-> PENDING_CUSTOMER. No reopening in the initial version.
2. Engineer acknowledgment sets first_response_at only once.
3. Add resolution storage: root cause, text, code, resolver, timestamp; require it
   before resolving. Desk closes resolved tickets and records closed_at.
4. Customer can cancel own OPEN ticket; desk can cancel other unresolved tickets
   with a reason. Add one feedback submission by owning customer after closure.
5. Each state transition includes timestamps/history/notification/audit atomically.

Acceptance: customer raises -> desk assigns -> engineer acknowledges/resolves ->
service desk closes -> customer submits feedback, entirely through the web UI.

## Milestone 7 - SLA monitoring and escalation

1. Compute response/resolution SLA independently. Approved defaults: continuous time,
   no pending-customer pause, at-risk at policy warning threshold (80% initially).
2. Desk priority changes select the new priority policy and recalculate deadlines from
   original creation time, not from time of change; audit the change.
3. Add escalation history, escalation level and previous operational status. Monitor
   every 60 seconds using ScheduledExecutorService.
4. Default levels: Team Lead at risk; Network Manager at breach; Operations Manager
   one additional resolution-SLA duration overdue. One entry per ticket/level, with
   configured recipients (no additional login roles).
5. ESCALATED retains previous operational status for explicit resumption; further
   levels remain in escalation history. Never infer SLA progress from status alone.
6. Use synchronized PriorityQueue processing for critical-first ordering; add SLA views.

Acceptance: fixed-Clock boundary tests cover warning/breach/completion/priority change;
repeated or overlapping monitor runs create no duplicate escalation/notifications.

## Milestone 8 - Notifications and network events

1. Add inbox/read actions and background notification processing; processing state
   and read state are separate. Bound retries and retain visible failure states.
2. Add network nodes, service-node links, events and event-ticket associations.
3. Dev simulator persists events then enqueues IDs in a bounded BlockingQueue;
   ExecutorService consumers call the existing ticket service.
4. One event may create tickets for multiple services, unique per event/service.
   Unmapped events remain for desk review. Recover pending jobs on restart.
5. Catch per-job errors and shut down executors cleanly.

Acceptance: simulated outage creates appropriate tickets; replay is idempotent;
restart recovers pending work; a failed event does not stop other consumers.

## Milestone 9 - Dashboards and reports

1. Finish all four dashboards using shared services.
2. Seven reports: Ticket Volume, SLA Compliance, Engineer Performance, Incident
   Category, Regional Incident, Average Resolution, Critical Incident.
3. Add async job request/status/authorized-download flow using Callable/Future.
4. Define date ranges/timezone explicitly, report response/resolution separately,
   exclude unresolved tickets from average duration while showing overdue work.
5. Demonstrate required Streams on bounded datasets; use SQL aggregates for larger
   reports. Export CSV/TXT with correct quoting and formula-prefix protection.

Acceptance: known fixtures produce correct totals, authorized downloads and usable
exports with commas, quotes and multiline user content.

## Milestone 10 - Assessment evidence and delivery

1. Add isolated raw JDBC examples for Connection/PreparedStatement/ResultSet, batch,
   commit/rollback/savepoint and required SQL CRUD/joins/grouping/subqueries/dates.
2. Map Core Java/Java 8/concurrency/security/file handling requirements to concrete
   examples; document DAO, Strategy and Factory patterns.
3. Run complete tests, fresh-database rehearsal and end-to-end demonstration.
4. Deliver setup/demo README, sample exports, architecture notes and known limits;
   verify no secrets or OTPs are committed/logged.

Acceptance: another developer can build, configure and demonstrate the case study
using only repository documentation and their own local credentials.

## Testing and milestone handoff

Unit tests: rules, ownership, OTP boundaries, ranking/no-match, SLA boundaries using
Clock. PostgreSQL integration tests: enum/constraint behavior, mapping, rollback,
concurrency and idempotency. MVC tests: roles/ownership/CSRF/form validation. Do not
run writable tests against the normal development database. The existing baseline
inspection is explicitly read-only and opt-in.

After every milestone update MILESTONES.md and report a concise summary to the user:
what is implemented, tests/results, actual migrations applied (separate from files
created), decisions/limits and exact next task. Record partial progress when pausing.
Each completed milestone should have a reviewable commit if a Git identity is available.

Retained schema assumptions: ticket customer derived through service (no ownership
transfers); one engineer specialization; workload derived; immutable SLA versions and
append-only status history; history actor remains a stable external reference until
user links are introduced. Display timezone is Asia/Kolkata; persisted instants use UTC.
