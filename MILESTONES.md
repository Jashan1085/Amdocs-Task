# TelecomOSS milestone tracker

Last updated: 16 October 2026 (Asia/Calcutta).

## Start here

Read this file, BUILD_PLAN.md, and README.md before editing. The approved choices
are Java 8, Boot 2.7.18, explicit SQL through JdbcTemplate, Spring-managed JDBC
transactions, Thymeleaf/Bootstrap, and local development OTP delivery initially.
There is no JPA. Do not expose credentials or overwrite local configuration.

## Status

| Milestone | Status | Result |
|---|---|---|
| Existing foundation | Complete | Boot entrypoint, six-entity SQL, seed/verification SQL, live SELECT 1 check, five connectivity tests |
| 1. Reproducible baseline | Complete | Java 8 setup, private config, versioned SQL and installed/verified dev database; first Git commit deferred for missing author identity |
| 2. Models and JDBC DAOs | Complete | Six models, eight enums, six read DAOs/mappers, paging, UTC Clock; isolated PostgreSQL mapping tests passed |
| 3. Authentication and shared UI | Complete | V002 applied; four demo logins, CAPTCHA/OTP/reset, lockout, secured Bootstrap dashboard shells; isolated and live checks passed |
| 4. Customer ticket workflow | Complete | Own-service listing, customer/desk intake and tracking, SLA targets, atomic history/notification/audit; V003 applied |
| 5. Assignment | Complete | Desk queue, top-three recommendations, atomic assignment/reassignment, version conflicts; V004 applied |
| 6. Manual lifecycle | Complete | Engineer acknowledgment/pending/resolution, desk closure, cancellation, one-time feedback; V005 applied |
| 7. SLA and escalation | Complete | Independent SLA evaluation, 60-second monitor, three-level escalation, priority changes, explicit resumption and manager counters; V006 applied |
| 8. Notifications and events | Complete | Recipient inboxes, independent read state, local delivery receipts/retries, node mappings, persisted dev event simulation and recovery; V007 applied |
| 9. Dashboards and reports | Complete | Four scoped operational dashboards, seven SQL reports and bounded asynchronous CSV/TXT jobs; no migration required |
| 10. Assessment and delivery | Complete | Scratch raw JDBC/Stream exercises, case-study resolution catalog, remaining SLA time, integrated rehearsal, samples, package verification and final handoff; V008 applied |

## Current database state

Configured local development database: **V001 through V008 APPLIED**. V008 expands
the resolution-code CHECK to accept the eight exact case-study values while preserving
five historical values and all metadata. Applied at **00:12 IST on 16 October**; all
eight checksums and four read-only checks passed at **00:13 IST**. Observed totals:
10 tickets, 33 history rows, 16 notifications, nine audits, zero receipts and zero
network events. This matches the milestone-8 reading; no final demonstration tickets
or dispatch runs were written to development. Writable rehearsals used scratch DBs.
Milestone 9 required no schema change; future migrations start at V009. V007 adds
notification retry fields/receipts and network nodes/mappings/events/ticket associations.
Applied at 23:47 IST; all seven checksums and four read-only checks passed at 23:50 IST.
Observed current totals: 10 tickets, 33 history rows, 16 notifications, nine audit
events, zero delivery receipts and zero network events. No demo nodes, mappings,
events, test tickets or live delivery runs were inserted/executed by milestone-8
verification. On web restart, pending notifications will be processed automatically.
V006 adds
escalation history/state/recipient configuration, escalation notifications and SYSTEM
audit actors. Applied at 23:26 IST; all six checksums and four read-only tests passed
at 23:27 IST. Observed current totals: 10 tickets, 33 history rows, 16 notifications,
nine audit events. These are live readings, not required fixture counts: ordinary
application activity and the enabled SLA monitor may advance them. No synthetic
milestone-7 test tickets were written to development; writable tests used disposable
databases. V006 does not update ticket rows; it adopts only recorded prior states for
existing ESCALATED tickets into the new state table. V005 adds
immutable resolution/cancellation/feedback records and the feedback event. V004 adds
category rules, engineer notification recipients and old/new engineer audit fields.
Read-only verification after V005 confirmed 10 tickets, 28 history rows, four
notifications and three audit events. All five checksums and history/SLA consistency
checks passed. No synthetic lifecycle tickets were written by milestone-6
development to this database; writable tests used disposable databases. V003 adds
notification and append-only audit tables plus the ticket-number sequence.
V002 adds five auth
tables and nullable customer/engineer user links. Four demo logins were provisioned;
their random initial passwords are in ignored config/dev-accounts.properties, never
in this document. The eight baseline tickets and their history remain intact;
additional tickets created during manual testing are retained.

## Milestone 1: reproducible baseline

Implemented:

- Installed Java 8 is discoverable by scripts/mvn.ps1; IntelliJ project SDK is
  already configured as temurin-1.8. Global JAVA_HOME points to an invalid JRE/bin
  path; the launcher avoids changing global settings.
- Existing datasource configuration was preserved in the ignored external
  config/application-local.properties file. Shared properties contain no credentials.
- Canonical baseline is db/migrations/V001__initial_schema.sql; db/schema.sql is a
  backwards-compatible psql entrypoint. No startup schema initialization.
- Explicit DatabaseSetup command refuses occupied schemas and commits baseline,
  optional fixtures and checksum ledger atomically. Unit tests cover rollback/refusal.
- Opt-in DatabaseSchemaIntegrationTest inspects the configured schema read-only.

Verified facts before installation:

- SELECT 1 through Spring succeeded on the installed Java 8 JDK.
- Read-only schema inspection found no public application tables. The existing SQL
  files had previously been tested only in a temporary PostgreSQL instance.
- Initial opt-in schema test failed as expected because the six tables were absent.
- Direct psql authentication was rejected; use the Spring-configured setup/test
  commands rather than attempting to parse Java properties as plain strings.

Completion verification (15 October 2026):

- scripts/mvn.ps1 -version selected Temurin Java 1.8.0_504 and Maven 3.9.9.
- scripts/mvn.ps1 -B -ntp -o test passed all 8 unit tests; the opt-in integration
  class was skipped as designed when telecom.integration was absent.
- scripts/Initialize-Database.ps1 -Seed successfully installed V001, fixtures and
  schema_migration in the configured local development database, at 18:32 IST.
- scripts/mvn.ps1 -B -ntp -o -Dtelecom.integration=true test passed all 11 tests:
  5 connectivity/profile tests, 3 installer tests, 3 live read-only integration checks.
- Live inspection confirmed six business tables plus schema_migration, both enabled
  history/SLA mutation guards, matching V001 checksum, eight seeded tickets,
  consistent history chains/current statuses and policy-derived deadlines.
- Repeating the actual installer returned a generic failure and exit code 1, correctly
  refusing the occupied schema before executing baseline or seed SQL.
- git check-ignore confirmed private local properties, IDE state and target output
  are ignored. SQL line endings are fixed to LF to preserve migration checksums.

Database state: **V001 APPLIED** to the configured local development database;
development seed **APPLIED** (3 customers, 5 services, 4 engineers, 4 SLA policies,
8 tickets, 25 status-history rows). The seed is not itself an independently numbered
migration; the V001 ledger row records seeded=true. No additional migration applied.

Git state: repository initialized on main, no commits yet. No Git user.name/user.email
is configured. Do not invent an identity; the baseline commit is deferred until the
user supplies or configures one. No remote or push was created.

Known limits: the installer only supports empty-schema V001 installation, not later
migrations or adoption of a manually installed schema. The checksum/seed checks are
for the current baseline; evolve integration tests with future migrations. Application
business behavior and screens are not implemented. Maven still requires an external
installation/cache; this milestone does not install a Maven wrapper.

## Milestone 2: models and JDBC DAOs

Completed: 15 October 2026. Scope stops before authentication and web workflows.

Implemented:

- model/: six immutable snapshots and eight enums matching the approved schema.
  Customer, TelecomService, NetworkEngineer, TroubleTicket, TicketStatusHistory,
  SLAConfiguration; separate Severity and TicketPriority types.
- dao/: six interfaces; dao/impl/: constructor-injected JdbcTemplate repositories
  with explicit columns, parameter binding, native enum casts and no write methods.
- dao/mapper/: six RowMappers plus shared null/enum/Instant conversion helpers.
  Nullable engineer IDs, lifecycle times, old history status and remarks stay null.
- Read APIs: IDs/business codes, services/customer, tickets/customer/engineer/status/
  priority, available engineers by region/skill, chronological history, versioned SLA
  and latest policy per priority. Customer ownership is joined through telecom_service.
- dto/PageRequest: limit 1-100, offset >= 0; all lists use stable, explicit ordering.
  Missing singles return Optional.empty; database failures propagate unchanged.
- config/TimeConfiguration: injectable UTC Clock, replaceable by a fixed test clock.
- scripts/Test-DaoIntegration.ps1: isolated PostgreSQL cluster on a generated free
  loopback port, explicit test environment only, cleanup/shutdown in finally.
- docs/DATA_ACCESS.md: exact interfaces, null/time/paging behavior and testing commands.

Verification:

- Java 8 ordinary test run passed **18 tests**, with the two live integration classes
  disabled by default (Surefire reports these as 2 skipped class placeholders).
- scripts/Test-DaoIntegration.ps1 passed **31 tests**: the same 18 unit tests plus
  13 real PostgreSQL DAO tests. One class placeholder was skipped for the unrelated
  opt-in development-database baseline checks. Zero failures/errors.
- PostgreSQL tests cover all six field mappings, enums, Optional/no-match paths,
  parameter injection resistance, NULL handling, non-UTC session time conversion,
  microsecond precision, leap-day dates, filter/ownership joins, stable page ordering,
  tied history times, Spring bean wiring and versioned/latest SLA behavior.
- Temporary test server was stopped and its generated cluster removed successfully.
  Development datasource settings were not loaded by the DAO integration tests.

Schema state: **no new migrations**. V001 and the development seed remain as applied
in milestone 1; no development schema/data was changed by milestone 2. V001 plus
extra test-only fixtures were installed solely in the now-removed temporary database.

Known boundaries: DAOs are internal persistence APIs, not authorized service methods.
There are no controllers/screens, mutations, recommendation ranking or SLA workflow
calculations yet. Offset paging is capped at 100 rows per request. Existing read-only
development baseline checks remain a separate opt-in suite. Git commit remains deferred
for the previously recorded missing author identity; no push was attempted.

## Milestone 3: authentication and shared UI

Completed: 15 October 2026. Stop before milestone 4.

Implemented:

- Immutable V001 retained. V002 adds app_user, app_role, user_role, login_history,
  otp_challenge and customer/network_engineer user_id links. DatabaseMigrate applies
  V002 explicitly, with checksums/advisory lock/atomic ledger entry, or verifies a no-op.
- scripts/Update-Database.ps1 supports optional -DevAccounts. Provisioning adds four
  hashed-password users and links customer.demo to CUST1001, engineer.demo to ENG1008.
  desk.demo and manager.demo have their named staff roles. No credential overwrites.
- JdbcAuthenticationDAO and transactional AuthenticationService implement password
  plus OTP login, reset, lock counters and audit history using the same JDBC transaction.
  Five password failures lock for 15 minutes; OTPs expire after five minutes and allow
  five wrong valid-format codes. Concurrent failures are serialized with user-row locks.
- BCrypt strength 10; reset password minimum 12 characters and maximum 72 UTF-8 bytes.
  Random six-digit OTPs are hashed, purpose/session-bound and single-use. CAPTCHA is
  a single-use, five-minute session challenge. Unknown reset requests are generic.
- Password success alone grants no access. OTP success rotates session ID/CSRF token;
  session principal contains no password/hash. Password reset atomically changes hash,
  increments auth_version, unlocks and invalidates all pending user challenges.
- SessionAccountFilter revokes stale/reset/disabled/locked/changed-role sessions and
  fails closed on database failure. Logout can run without database availability.
- SecurityConfiguration enforces role routes and CSRF, disables password-only/basic
  login, and suppresses Boot's generated user. Default session timeout is 30 minutes;
  HttpOnly/SameSite=Lax cookies. No automatic schema/account changes at startup.
- DevOtpDelivery holds bounded expiring messages in memory. /dev/otp reads only the
  current browser session's pending challenge, not a supplied ID. Without dev, inbox
  controller is absent and delivery fails closed. SMTP is intentionally not implemented.
- Shared Thymeleaf/locally served Bootstrap layout, validated sign-in/reset forms,
  accessible labels/focus styles, responsive CSS, four protected dashboard shells,
  and generic 403/404/500/503 views. No invented dashboard metrics or ticket operations.
- docs/AUTHENTICATION.md documents routes, policies, local use, schema and test commands.

Verification:

- Java 8 full isolated run: scripts/Test-AuthIntegration.ps1 passed **65 tests**:
  29 unit tests + 23 full authentication MVC/JDBC/template tests + 13 DAO integration
  tests. One placeholder for the separate dev-baseline suite was intentionally skipped.
- Tests cover all four role logins/denials, password-only denial, session rotation,
  CAPTCHA/OTP expiry/reuse/binding, concurrent account lockout, reset confirmation and
  revocation, unavailable delivery, CSRF/logout, generic DB failure pages, local CSS,
  checksum refusal/idempotency, and transactional rollback on success-history failure.
- Both scratch databases/cluster were shut down and removed. They did not load the
  private development datasource configuration.
- Update-Database.ps1 -DevAccounts succeeded against the configured development DB:
  **V002 applied**, four demo accounts provisioned, password file created and ignored.
- Three read-only DatabaseSchemaIntegrationTest checks passed after migration, verifying
  V001 checksum/guards, original seeded tickets, deadlines and status-history continuity.
- Live HTTP smoke test on the separate verification instance (port 18080) passed for
  customer.demo, desk.demo, engineer.demo and manager.demo: real password+CAPTCHA+OTP,
  authorized workspace, wrong-role 403 and logout. Passwords/OTPs were not printed.
- Verification instance was stopped; any existing user-run app instance was left alone.

Schema state: V002 is recorded in schema_migration with its checksum. Its seeded flag
is false (demo provisioning is separate, not a numbered migration). V001/seed data
remain in place. No V003 migration exists yet.

Known boundaries: dashboard pages are shells; customer service/ticket access and
business mutations belong to milestone 4 onward. OTP is local-demo only; use the dev
profile to sign in. Browser sessions/in-memory delivery are local to one app instance.
Demo passwords in the ignored file are initial values; reset does not rewrite the file.
Fresh setup requires V001 first, then V002; do not rerun initial setup on this database.
The migration runner knows versions 1-2 and must be extended for future migrations.
Git author identity remains unconfigured; no commit/push has been invented.

## Milestone 4: customer ticket creation and tracking

Completed: 15 October 2026. Scope stops before assignment (milestone 5).

Implemented:

- Customer service listing with active-service ticket creation links; own-ticket
  list/detail/history with independent status/priority filters and 20-row pagination.
- Service-desk exact customer-number lookup, service listing, ticket creation on
  behalf of the actual owner, and ticket list/detail/history.
- SLAService selects the latest immutable policy version and uses one Clock instant
  for creation and separate response/resolution deadlines. Persistence is UTC;
  display is Asia/Kolkata. Policy defaults remain from the seed, not hard-coded UI.
- TicketWorkflowService reloads current roles/profile/version/lock state; rejects
  foreign services/tickets, inactive customer/service creation, and invalid forms.
  Actor and owner are derived on the server; request-supplied IDs cannot override them.
- Creation locks actor/service/customer rows and writes OPEN ticket + initial
  history + PENDING customer notification + audit in one JDBC transaction. Numeric
  IDs and display numbers are database-generated; new numbers use TT-0000000001
  format, distinct from seed numbers, and may have gaps after rollback.
- Additive V003 adds notification processing/read fields, append-only audit and
  ticket_number_seq with useful indexes. DatabaseMigrate supports contiguous V001-3
  checksum verification and atomic application of pending versions; no startup DDL.
- Workspace navigation, validation messages, CSRF-protected forms, escaped content
  and generic safe errors. See docs/TICKET_WORKFLOW.md for routes/rules and handoff.

Verified:

- scripts/Test-AuthIntegration.ps1: 87 passed, one intentional skip (88 total),
  including 35 authentication/workflow integration cases, five SLA rule cases and
  the existing 13 DAO integration cases in disposable databases. Temporary cluster
  shut down and removed. Tests cover all roles, foreign access, invalid forms,
  active-state rules, latest policy, stale profile link, concurrent unique numbers,
  paging, XSS escaping, append-only audit, changed checksums and all-or-nothing
  rollback after history/notification/audit failures.
- Earlier runs exposed a test fixture code-length error, desk-route detection
  relying on servletPath in MockMvc, and an old global seed-count assertion. All
  were corrected before the complete passing run.
- scripts/Update-Database.ps1 succeeded at 20:48 IST on the configured development
  DB: **V003 APPLIED** without reprovisioning accounts or changing V001/V002.
- Four read-only DatabaseSchemaIntegrationTest checks passed at 20:55 IST: all
  three checksums matched, audit guard/sequence existed, eight original tickets and
  25 history entries retained valid policy deadlines/history continuity. No live
  ticket creation was performed; business write tests used disposable databases.

Limits: notification rows are durable PENDING records; inbox/dispatch/retries are
milestone 8. Engineer assignment and status changes are later milestones. Customer
and desk select initial priority/severity; inactive services/customers accept no new
tickets. Duplicate form submissions are separate tickets (no idempotency key).
Git author identity remains absent, so no commit/push was invented. Existing IntelliJ
instance was left running; restart TelecomOSS Dev to load the new routes/templates.

## Milestone 5: engineer assignment and reassignment

Completed: 15 October 2026. Scope stops before manual lifecycle (milestone 6).

Implemented:

- Desk Assignment queue, initially OPEN, with existing status/priority filters and
  pagination. Ticket detail shows engineer name/code for customer and desk, and
  desk-only assignment/reassignment links where supported.
- V004 category_specialization configuration covers all ten categories with initial
  mappings documented in docs/ASSIGNMENT.md. No existing engineer settings changed.
- At most three available skill/region matches ranked by derived active workload,
  descending experience and employee code (UTF-8/C order). SQL selects the global
  top three; EngineerRanking uses Java 8 Streams/comparators consistently. Missing
  mapping/no match/alternative is explicit; no fallback to ineligible engineers.
- AssignmentService reloads the current desk actor, locks ticket/service/customer,
  category rule and engineers, rechecks eligibility, and rejects stale row_version
  with HTTP 409. Engineer locks use ascending ID order to support concurrent swaps.
- OPEN becomes ASSIGNED with history. Reassignment preserves ASSIGNED, IN_PROGRESS
  or PENDING_CUSTOMER and creates audit only for engineer changes, with mandatory
  reason. Resolved/closed/cancelled and ESCALATED tickets are rejected for this flow.
  Deadlines, policy and acknowledgment timestamps remain unchanged.
- V004 adds exactly-one customer/engineer notification recipient constraint and
  previous/new engineer plus reason audit columns. Assignment update, optional
  status history, two PENDING notifications and audit commit or roll back together.
- CSRF forms, server-side role checks, friendly no-match/version messages and
  explicit no-op rejection. No inbox/dispatch or engineer lifecycle actions yet.

Verified:

- scripts/Test-AuthIntegration.ps1 final run: **115 passed, one intentional skip
  (116 total)**, including 59 authentication/workflow/assignment integration cases,
  three ranking cases, V004 rollback/refusal and the existing DAO/SLA suites.
- Tests cover actual workload/terminal exclusion, sorting/top-three/ties, missing
  rules, wrong skill/region/availability, changes after recommendation, role/CSRF,
  stale browser forms, first assignment versus reassignment history, blocked states,
  required reasons, no-op rejection, recipient constraints and V004 checksums.
- Concurrent desk writes produce one winner/one conflict; simultaneous swaps
  succeed without deadlock. History/notification/audit failures roll back assignment,
  version and all related rows; reassignment audit failure restores old engineer.
- Disposable cluster shut down and removed. The initial and final runs passed;
  the final run included the added edge/concurrency cases.
- scripts/Update-Database.ps1 succeeded at **21:25 IST**: **V004 APPLIED** to the
  configured development database, without reprovisioning logins or ticket writes.
- Four read-only DatabaseSchemaIntegrationTest cases passed at **21:26 IST**:
  V001-V004 checksums, ten rules, valid recipients, enabled audit guard, retained
  original fixtures and consistent history/deadlines. Observed totals: 10 tickets,
  27 history rows, two notifications and two audit events.

Limits and handoff: category mappings are initial configuration with no management
UI; availability flag/skill/region determine eligibility, not optional engineer
login provisioning. Recommendation workload is advisory, with no capacity limit.
ESCALATED reassignment awaits milestone 7's operational-state handling. New engineer
and customer receive pending records; delivery/inbox remains milestone 8. Existing
IntelliJ instance was left alone; restart TelecomOSS Dev to load new routes/templates.
Git identity is still absent; no author was invented and no commit/push was made.
See docs/ASSIGNMENT.md for routes, lock order, rules and a manual demonstration.

## Milestone 6: manual lifecycle and functional MVP

Completed: 15 October 2026. Stop before SLA monitoring/escalation (milestone 7).

Implemented:

- TicketTransitions centralizes the manual status graph and role/action policy.
  Assignment uses its OPEN-to-ASSIGNED edge; no reopening or same-status transition.
- Engineer workspace links to a filtered/paged list of currently assigned tickets
  and authorized detail/history. Current database profile links and ticket assignment
  determine access; a former engineer loses read/write access after reassignment.
- Assigned engineer acknowledgment sets first_response_at once; IN_PROGRESS can
  request customer information with a reason, resume from PENDING_CUSTOMER, or
  resolve with mandatory root cause, text and controlled resolution code. None of
  these actions changes SLA deadlines/policy or pauses time.
- Desk closes RESOLVED tickets only when structured resolution exists, setting
  closed_at. Customer cancels own OPEN ticket; desk cancels unresolved tickets
  (including ESCALATED) with reason. Resolved/closed cancellation is prohibited.
- Owning customer may submit one rating (1-5) and optional comments after closure.
  Feedback increments optimistic version but does not create a fake status-history
  entry. Ticket key/locks prevent concurrent duplicate submissions.
- V005 adds ticket_resolution, ticket_cancellation and ticket_feedback, actor/profile
  foreign keys, indexes and append-only guards. Notification catalog gains
  FEEDBACK_RECEIVED. Existing tickets are preserved without invented metadata.
- LifecycleService locks actor/ticket/service/customer and validates current profile
  links, ownership, role, state and row_version. Status/timestamps, metadata, history,
  customer/assigned-engineer PENDING notifications and audit commit atomically.
- New action/feedback forms and shared lifecycle details show escaped reasons,
  resolution, feedback and closure timestamps. All mutation forms use CSRF. Updated
  docs/LIFECYCLE.md contains a complete customer.demo/desk.demo/engineer.demo demo.

Verified:

- Final scripts/Test-AuthIntegration.ps1: **137 passed, one intentional skip
  (138 total)**. Includes 71 full authentication/workflow/assignment/lifecycle
  integration cases, nine graph/policy cases, V005 rollback and prior DAO/ranking/SLA
  suites. Both initial and final milestone-6 runs passed; final run includes current
  profile and terminal-state edge cases. Disposable PostgreSQL cluster removed.
- Full web-form lifecycle tests cover engineer list/detail, acknowledgment,
  customer-wait/resume, required resolution, desk close and customer feedback with
  escaped user content. First-response and independent SLA deadlines are preserved.
- Tests reject former/foreign engineers, changed profile links, wrong roles, missing
  CSRF, missing reasons/resolution, stale versions, invalid state changes, duplicate
  closure, reopening, pre-closure feedback and ratings outside 1-5.
- Concurrent feedback has one winner/one conflict. Resolution/history/notification/
  audit failures roll back the whole transition. Cancellation/feedback audit failure
  removes partial metadata and restores ticket version. Metadata mutation is refused.
- scripts/Update-Database.ps1 succeeded at **22:58 IST**: **V005 APPLIED** to the
  configured development database without changing legacy tickets or reprovisioning.
- Four read-only DatabaseSchemaIntegrationTest cases passed at **22:59 IST**:
  V001-V005 checksums, baseline fixture/history/SLA continuity, metadata guards and
  consistency, category rules and notification recipients. Observed: 10 tickets,
  28 history entries, four notifications, three audit events.

Limits: legacy resolved seed tickets have no resolution records, remain readable,
and cannot be closed until reviewed data is adopted/backfilled; the app explicitly
explains this rather than inventing a resolver/root cause. Use a fresh NETWORK_OUTAGE
ticket on SVC-MOB-001 and assign ENG1008 for the full engineer.demo demo. Notification
inbox/delivery and manager reporting remain later milestones. Existing IntelliJ
instance was left alone; restart TelecomOSS Dev for new routes/templates. Git author
identity remains absent; no identity, commit or push was invented.

## Milestone 7: SLA monitoring and escalation

Completed: 15 October 2026. Stop before milestone 8.

Implemented:

- SlaEvaluation/SLAService assess response and resolution independently with inclusive
  warning/deadline boundaries, stored immutable policy versions and injectable Clock.
  Completion at the deadline is MET; late completion remains BREACHED but does not
  trigger active escalation. Pending-customer time runs continuously; cancelled work
  is NOT_APPLICABLE. Active counters exclude resolved/closed/cancelled tickets.
- V006 adds escalation_recipient, ticket_escalation_state, append-only escalation_history,
  third notification-recipient type and explicit USER/SYSTEM audit actor constraints.
  DatabaseMigrate verifies contiguous V001-V006 checksums and applies pending SQL
  atomically. Existing migrations and private configuration remain unchanged.
- SlaScheduler uses ScheduledExecutorService, first check after 60 seconds, fixed
  60-second delay, start-once protection and clean shutdown. Web-only and configurable
  with telecom.sla.monitor.enabled; disabled for isolated writable MVC tests. CLI tools
  and read-only schema checks never start it.
- SlaMonitor scans persisted active tickets in keyset pages of 200, feeds a synchronized
  PriorityQueue, and processes CRITICAL/HIGH/MEDIUM/LOW then ticket ID. Failed jobs retry
  next scan without stopping others. Restart rescans persisted work; lock timeout is
  five seconds. A priority change during scanning can defer work until next scan.
- EscalationService locks/rechecks each ticket, recording Team Lead at risk, Network
  Manager at breach, Operations Manager one extra resolution-duration overdue. Catch-up
  records all due levels at detection time. Unique ticket/level and recipient indexes
  prevent duplicates across repeated/overlapping runs. Status/version, saved operational
  state, escalation records, PENDING notifications and SYSTEM audit commit atomically.
- ESCALATED stores its explicit prior operational status. Further levels do not create
  invalid same-status history. Desk resumption restores only that recorded state with
  a reason/version check, preserving deadlines, first response and highest level. Unknown
  legacy state blocks resumption; no state is inferred from timestamps. Ordinary engineer
  actions cannot bypass this flow. Desk cancellation remains available.
- Desk priority changes select the latest policy for the new priority, recalculate
  deadlines from ORIGINAL creation time, and audit old/new priority/policy. No synthetic
  status-history row. Notification/audit failure rolls back the whole change.
- Customer/engineer authorized ticket detail shows SLA/escalation data. Desk and manager
  dashboards link to filtered, paged SLA views and global active counters. Only desk can
  change priority/resume, with CSRF/current role checks. See docs/SLA_MONITORING.md for
  routes, rules, entrypoints, concurrency and a manual verification walkthrough.

Verified:

- Final scripts/Test-AuthIntegration.ps1 at **23:25 IST**: **167 passed, one intentional
  skip (168 total)**; zero failures/errors. Includes 83 authentication/workflow/lifecycle/
  SLA integration cases, 14 new SLA boundary cases, three queue/scheduler cases, six
  migration safety tests and existing DAO suites. Disposable cluster stopped/removed.
- Covered exact warning, deadline and extra-duration thresholds; met/late completion;
  cancelled/resolved work; original-time/latest-policy priority changes; explicit saved
  status restoration; stale versions, wrong roles and missing CSRF; independent SQL
  counters; configured recipient failure; legacy state; immutable escalation history;
  overlapping checks and repeated full scans without duplicate events/notifications.
- Failures at escalation history/notification/audit roll back every partial write.
  Priority notification failure restores priority/policy/deadlines/audit. Resumption
  audit failure restores ESCALATED, prior state, version and notification/history counts.
  V006 DDL failure rolls back without replaying existing migrations or recording V006.
- scripts/Update-Database.ps1 succeeded at **23:26 IST**: **V006 APPLIED** on configured
  development DB, without reprovisioning accounts or inserting test tickets.
- Four read-only DatabaseSchemaIntegrationTest cases passed at **23:27 IST**: V001-V006
  checksums, history/deadline continuity, fixture retention, lifecycle/escalation guards,
  one-of-three notification recipients, escalation level consistency and valid audit
  actors. Live counts are recorded above. Private config and target remain Git-ignored.

Limits: escalation recipients have example.test placeholder contacts, no new login
roles or SMTP delivery. Notifications remain PENDING until milestone 8. Escalation levels
are once per ticket lifetime; resumption/priority change do not reset them. Fixed-date
seed tickets can escalate using the current clock. Restart TelecomOSS Dev to load all
changes; the enabled web monitor can then change due tickets automatically. No separate
live browser mutation test was needed; web flows were exercised with real PostgreSQL
through MockMvc. Git identity remains absent, so no identity/commit/push was invented.

## Milestone 8: notification inbox, processing and network events

Completed: 15 October 2026. Stop before milestone 9.

Implemented:

- Recipient inboxes on all four dashboard routes: customer owns profile notifications;
  engineer requires current profile/assignment; manager shares configured escalation
  recipient messages; desk sees read-only processing oversight. Lists use 20-row pages,
  unread filters and escaped messages. Recipient mark-read uses CSRF/current roles,
  idempotent first read_at and does not change processing state or attempts.
- NotificationService/JdbcNotificationDAO process up to 100 due records every five
  seconds. FOR UPDATE SKIP LOCKED and a unique immutable notification_receipt give
  atomic, idempotent local IN_APP delivery. NotificationDelivery is a transactional
  interface, implemented by InAppNotificationDelivery. No email/SMS is sent.
- Failed delivery rolls back to a nested JDBC savepoint, including partially inserted
  receipts and aborted SQL transactions. The outer transaction preserves attempt/error
  state. Three attempts, 60-second retry delay, then visible FAILED; no automatic reset.
  Stored/public errors are fixed safe codes, never raw SQL/connection details.
- V007 adds network_node, service_network_node, network_event, immutable event_ticket
  and notification_receipt, plus retry fields/indexes. UUID event keys are unique;
  (event,service) and ticket association constraints prevent duplicates and enforce
  that the ticket belongs to its linked service. V001-V006 are unchanged.
- Desk can register nodes, link exact service codes, list/review events and inspect
  created tickets. Node/service mappings are many-to-many. The dev-only simulator
  persists its event before enqueueing. Reusing the same form/key is idempotent;
  conflicting payload/actor reuse returns 409. No live demo fixtures were installed.
- OUTAGE creates NETWORK_OUTAGE/CRITICAL tickets; DEGRADATION creates SLOW_DATA/HIGH
  tickets, with matching severity. Only active customers/services qualify. Existing
  TicketWorkflowService performs system creation inside the event transaction using
  shared service/customer checks, sequence numbers, latest SLA policy and insertion.
  Initial history/audit have explicit SYSTEM event provenance; original submitting
  desk user is stored on the event. No fake login or public system-ticket endpoint.
- One event fans out to mapped services, read in ascending batches of 50. Node locks
  stabilize mappings; the complete event's tickets/associations/history/notifications/
  audits commit atomically. Failure on a later service rolls back earlier services.
  UNMAPPED retains work with no eligible mappings for desk review. After mapping fixes,
  desk may retry UNMAPPED/FAILED; PROCESSED cannot be reset. Explicit retry_count is
  retained while starting a new bounded three-attempt processing cycle.
- EventDispatcher has a 256-ID ArrayBlockingQueue and in-flight deduplication. Two
  ExecutorService consumers process jobs; database locks/uniqueness protect across app
  instances. Full queues retain persisted work for startup/15-second recovery scans.
  Per-job errors do not stop other work; legacy PROCESSING and pending jobs recover.
- BackgroundJobs owns scan/consumer executors, starts once at ApplicationReadyEvent,
  and interrupts/awaits clean shutdown. Web-only telecom.jobs.enabled defaults true;
  migrations and read-only CLI checks never launch it. Writable tests disable it and
  the SLA monitor. See docs/NOTIFICATIONS_EVENTS.md for entrypoints and local walkthrough.

Verified:

- Final scripts/Test-AuthIntegration.ps1 passed at **23:47 IST**: **186 passed, one
  intentional skip (187 total)**, zero failures/errors. Includes 98 MVC/security/JDBC
  integration cases, three background queue/lifecycle unit cases, seven migration
  safety cases and existing DAO/SLA/lifecycle tests. Disposable cluster stopped/removed.
- Tests cover read/delivery independence, ownership/current engineer assignment,
  manager recipient separation, desk oversight, CSRF/XSS, duplicate receipts,
  overlapping notification/event workers, retries/max attempts and SQL savepoint
  recovery; persistent event replay, multi-service fan-out, unmapped review, key
  collisions, active-state checks, failure isolation and startup recovery with a fresh
  queue. Queue bounds and executor start-once/shutdown are covered by unit tests.
- Event history/notification/audit/association failures leave no partial ticket. A
  second-service failure rolls back the first and a later retry creates both once.
  V007 DDL failure rolls back without recording its version or replaying earlier SQL.
- Initial run exposed three fixture assumptions (global batch size, required engineer
  assignment and previously inserted latest SLA policy); fixtures were corrected,
  then the complete suite passed. No product rules were weakened for those tests.
- scripts/Update-Database.ps1 succeeded at **23:47 IST**: **V007 APPLIED** to configured
  development DB without reprovisioning logins or installing synthetic business data.
- Four read-only DatabaseSchemaIntegrationTest checks passed at **23:50 IST**:
  V001-V007 checksums, schema/immutability guards, fixture/history/deadline continuity,
  recipient/audit/escalation validity, receipt consistency and event/service-ticket
  linkage. Live counts are recorded above; zero receipts/events before web restart.

Limits/handoff: delivery is local in-app, not SMTP/SMS; configured contact addresses
are not contacted. FAILED notifications remain visible for desk review without a
manual retry screen. Managers share escalation inbox read state. Node editing,
unlinking/deactivation screens and external monitoring adapters are outside this
initial implementation. Fan-out is one transaction even though service reads are
paged; large nodes may need a future measured scaling change. Restart TelecomOSS Dev
for new screens/jobs. Live browser mutations were not run; web flows used MockMvc and
real disposable PostgreSQL. Git commit remains deferred for the previously recorded
missing author identity; no identity/commit/push was invented. No reports added yet.

## Milestone 9: operational dashboards and asynchronous reports

Completed: 16 October 2026. User authorized completion of all remaining milestones.

- DashboardService/JdbcDashboardDAO now provide all four role dashboards with scoped
  ticket/service/notification totals, active/critical/escalated work, independent overdue
  targets, resolved-today and completed-only average resolution. Staff additionally see
  event failures, SLA warnings and top-five engineer workloads. Recent work is bounded
  and customer/engineer links retain normal ownership/current-assignment authorization.
- Seven SQL reports are selected by closed ReportType/ReportQueryFactory; dates are
  inclusive Kolkata creation dates with UTC timestamp exports. Independent completed/
  late/open-overdue SLA metrics and completed-only duration averages are explicit.
  Engineer performance attributes current assignment, not historic resolver credit.
- ReportService uses Callable/FutureTask with two workers/queue 16, three active jobs
  per user, 32 retained jobs, 5000 rows, 1 MiB and 20-second SQL timeout. Role/profile
  and ownership checks protect request/status/download/delete; CSRF applies to writes.
  Expiry is 30 minutes; results are in-memory and cleared on restart. Busy returns 429;
  failures are generic and isolated. Cancel/delete releases queued capacity.
- ReportEncoder exports correctly quoted CSV and tab-separated TXT, preserves Unicode,
  quotes and multiline cells, and neutralizes formula prefixes including whitespace.
  Attachment filenames are generated; no-store/nosniff headers protect downloads.
- Final milestone-9 Test-AuthIntegration.ps1 at **00:03 IST**: **195 passed, one
  intentional skip (196 total)**, zero failures/errors. Includes 102 MVC/JDBC/security
  integration cases plus encoder/job lifecycle tests. Disposable cluster removed.
  All seven SQL shapes, known SLA/duration counts, midnight boundaries, role/profile
  dashboards, cross-user jobs, CSRF, Unicode/formulas, expiry/cancellation/limits and
  failure isolation were checked. No schema migration or live business writes needed.
- docs/REPORTS.md documents semantics, routes, limits and local verification. Full
  assessment and final combined verification continue under milestone 10 below.

## Milestone 10: assessment evidence and delivery

Completed: **16 October 2026**. All ten approved milestones are complete.

Implemented:

- RawJdbcAssessment is a plain scratch-only utility, never a bean or endpoint. It
  rejects normal development connection URLs before SQL, uses temporary tables with
  ON COMMIT DROP, bound batch inserts/UPDATE/DELETE, explicit Connection/Statement/
  PreparedStatement/ResultSet resources, Savepoint recovery, commit and full rollback.
  Its queries demonstrate JOIN, LEFT JOIN, GROUP BY, HAVING, ORDER BY, subquery, CASE,
  aggregate/date functions. Known aggregate evidence is asserted, along with unchanged
  application ticket/history counts. A unit guard test refuses the dev datasource.
- BoundedTicketAnalytics demonstrates the nine requested Stream analyses on at most
  1000 explicit snapshots: status, priority, region, engineer workload/performance,
  active SLA breaches, completed-only average resolution, top categories and repeated
  incident customers. TicketMetric includes functional/default/static interface methods.
  Production report aggregation remains SQL; no unbounded stream reads were added.
- ReportFormatterFactory creates CSV/TXT formatting strategies via ReportFormatter;
  shared encoding is used by downloads and SampleExports. DAO, Factory, Strategy and
  lifecycle observer examples are mapped explicitly in docs/ASSESSMENT.md, along with
  classes/encapsulation/inheritance/interfaces/generics/security/concurrency/file I/O.
- Source-document review found the earlier resolution-code catalog missing its eight
  example values. Additive V008 and the enum now support all eight, preserving all five
  old values and immutable historical records. All 13 values were integration-tested.
  SLA detail also displays signed remaining seconds, frozen at target completion;
  cancellation hides inapplicable remaining time. Exact remaining-time tests added.
- The final integrated rehearsal covers MVC outage simulation, deterministic background
  processing, desk assignment, engineer acknowledgment/resolution, desk closure,
  customer feedback, delivery/read of notification, manager report download and replay
  protection. It runs against a newly installed/upgraded disposable PostgreSQL DB.
- docs/DEMO.md is the fresh setup/IntelliJ/packaged/demo guide; REPORTS.md documents all
  seven metrics/job permissions/limits; ASSESSMENT.md is the requirement/test matrix;
  ARCHITECTURE.md records layers/invariants/known limits. README/BUILD_PLAN/AGENTS and
  this tracker now point to the completed handoff. No line/branch percentage is claimed.
- scripts/Verify-Delivery.ps1 verifies package bytecode/private-config exclusion,
  generates docs/samples/synthetic-incidents.csv/.txt through the real formatter,
  then starts a separate loopback JAR instance with read-only datasource and background
  jobs disabled. It checks login/CAPTCHA and protected route redirect and stops only
  its own helper process. Existing IntelliJ processes/configuration are preserved.

Final verification:

- Test-AuthIntegration.ps1 at **00:09 IST**: **214 passed, one intentional opt-in skip
  (215 total)**, zero failures/errors. Includes 117 full MVC/JDBC/security cases,
  15 SLA evaluation cases, encoder/job/assessment units, eight migration safety cases
  and 13 original DAO cases. A fresh V001-to-V008 installation/rehearsal passed; the
  temporary PostgreSQL instance was shut down and removed.
- V008 migration succeeded on configured local dev DB at **00:12 IST**, without
  reprovisioning accounts or business-data writes. The four read-only schema tests
  passed at **00:13 IST**, including all eight checksums, resolution-code compatibility,
  mutation guards, baseline/history/deadline and recipient/event consistency.
- Maven runnable-JAR packaging passed at **00:12 IST**. The first offline packaging
  attempt lacked standard jar-plugin dependencies; a normal Maven packaging run
  downloaded those missing artifacts and succeeded. No app dependencies were changed.
- Verify-Delivery passed: Java 8 bytecode, no private configuration in JAR, synthetic
  samples generated, packaged login/CAPTCHA 200 and unauthorized manager redirect.
  The separate helper process stopped successfully; normal background jobs were disabled.
- An independent CSV parser read both synthetic formats: three rows/five columns,
  embedded quotes/newline preserved, formula prefixed safely. Shared config/docs scan
  accepted only blank passwords or the explicit example placeholder; ignored private
  files were not read by that scan. Git ignore confirms private configuration and target excluded.
- Verified artifact: target/telecom-assurance-0.0.1-SNAPSHOT.jar. SHA-256:
  B9E9345083B0897CC4F1BEF0890260F759B6AFF62EB7330A23A4610767D3AE22.

Delivery limits: the approved local development OTP and in-app delivery remain; no
SMTP/SMS/production deployment was added. Reports are bounded and process-local;
restart clears jobs/results. Legacy seed resolutions are not fabricated; use fresh
tickets for closure/feedback. Other domain scope choices remain in ARCHITECTURE.md.
No live browser business mutation was needed; full flows used MockMvc/scratch PostgreSQL,
plus an independent packaged HTTP smoke test. Git commit remains deferred for the
previously recorded missing author identity; no author, commit or push was invented.

## Required completion record

For each milestone record: completion date, behavior implemented, relevant entrypoints,
tests and results, exact schema versions applied and to which environment (no secrets),
limitations, decisions, and next task. Report the same concise summary to the user.
Do not mark complete until its acceptance checks pass. On a pause, record partial work.

## Historical authentication troubleshooting

Authentication flow review (15 October 2026): replaced the fixed example in the
CAPTCHA help with an instruction to answer the actual displayed addition, and labelled
login/verification Step 1/Step 2. The development delivery adapter now previews its
session-bound code directly on the login verification and reset forms; no inbox trip
is needed. It still requires explicit code entry and server-side verification.
Non-development delivery never adds this model value. GET /login now preserves a
live LOGIN pending challenge by returning to verification; viewing recovery no longer
silently deletes pending verification. An explicit CSRF-protected POST /login/restart
clears pending verification. Expired pending handles are discarded when checking the
appropriate verification flow. The optional /dev/otp diagnostic inbox remains.
Updated handoff/readme/auth docs. Test-AuthIntegration.ps1: 71 passed, one intentional
skip (72 total), including all 25 authentication integration tests. Coverage verifies
inline code rendering/no-store, cross-session isolation, navigation preservation,
restart CSRF and expired pending handles, in addition to existing four-role full login,
CAPTCHA, OTP, reset and DAO tests. No schema or private configuration changes. Restart
TelecomOSS Dev and reload /login to load these controller/template changes. Milestone
4 remains unauthorized.

CAPTCHA validation fix (15 October 2026): reproduced a live failure by loading two
login forms in the same HTTP session and submitting the correct answer from the
first. The old single session attribute had been overwritten by the second page.
Replaced it with up to 20 independently identified, session-bound challenges. Both
login and recovery forms now submit a hidden captchaId; DTOs/controller validate
the corresponding stored answer hash. Challenges remain single-use with five-minute
expiry; incorrect answers consume only their own challenge. Expired entries are
pruned and the oldest is evicted at capacity. No answer is sent in a hidden field.
Added unit coverage for independent forms, wrong-answer isolation, absent/unknown
IDs, replay, session isolation, expiry and storage bounds; added a real MVC/JDBC
regression for a login form submitted after other login/recovery page loads.
Test-AuthIntegration.ps1 passed 70 tests, one intentional skip (71 total), including
7 CAPTCHA unit tests and 24 authentication integration cases; temporary cluster was
removed. No schema/password changes. The initial live reproduction recorded an
ordinary CAPTCHA failure. Restart TelecomOSS Dev and reload /login before retrying;
previously rendered forms lack the new ID and must not be reused. Milestone 4 remains
unauthorized.

Follow-up login diagnosis (15 October 2026): after the IntelliJ dev restart,
/dev/otp returned 302 to /login as expected for a fresh session. A live HTTP check
using the saved customer demo password and a freshly solved CAPTCHA reached
/login/otp. Recent user attempts were CAPTCHA_FAILURE; customer.demo had zero failed
password attempts and was unlocked. Made the login CAPTCHA instructions explicit
(numeric sum, one tab, five-minute expiry) and added a specific CAPTCHA failure
message without revealing account state. Extended the existing integration assertion.
Test-AuthIntegration.ps1 passed 65 tests with one intentional skip (66 total),
including all 23 authentication integration tests, against disposable databases.
The live probe created an ordinary pending OTP only; no password, schema or business
data changes. Restart IntelliJ to load the clearer controller/template messages.

Login troubleshooting (15 October 2026): the existing IntelliJ instance on port
8080 served /login but returned 404 for /dev/otp, showing that the development OTP
controller was inactive. Added shared `.run/TelecomOSS Dev.run.xml` with the dev
profile and project-root working directory. Select it and restart the existing
instance in IntelliJ. A previous read-only check confirmed the customer demo initial
password matched, the account was enabled/unlocked, and its failed attempt was a
CAPTCHA failure. No schema or account changes were made for this configuration fix.

## Current handoff

**All ten milestones are complete. No planned implementation work remains.**

For future changes, read this tracker and docs/DEMO.md/ARCHITECTURE.md first, then the
relevant feature document. Preserve V001-V008 and use additive V009 onward for schema
changes. Do not rerun baseline/seed or writable tests on development. Reports currently
use process-local jobs; durable/multi-instance reports, real external delivery and
production deployment require separately scoped work. Existing IntelliJ app should be
restarted to load final dashboards/reports/resolution catalog/remaining SLA display.

Useful commands from the project root:

```powershell
.\scripts\mvn.ps1 test
.\scripts\Test-DaoIntegration.ps1
.\scripts\Test-AuthIntegration.ps1
.\scripts\mvn.ps1 '-Dtelecom.integration=true' '-Dtest=DatabaseSchemaIntegrationTest' test
.\scripts\mvn.ps1 spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Do not run Initialize-Database.ps1 against this already-populated database again;
its refusal behavior has already been verified.

For local login, restart with the dev profile and open /login. Demo usernames and
initial passwords are in ignored config/dev-accounts.properties. Read the development
OTP directly on step 2. Customer/desk workspaces now link to services and tickets.
