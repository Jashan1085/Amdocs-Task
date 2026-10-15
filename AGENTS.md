# TelecomOSS handoff

Read MILESTONES.md first, then BUILD_PLAN.md and README.md. Follow the active user's
milestone scope. All ten approved milestones are complete. Read docs/DEMO.md,
docs/REPORTS.md, docs/ASSESSMENT.md and docs/ARCHITECTURE.md for final handoff.
The GitHub-facing README is indexed; docs/README.md is the documentation hub,
docs/WORKFLOWS.md covers role journeys, and docs/design/ contains the system/class/
ER/state/sequence diagrams. Keep those views aligned with source when changing behavior.

- Java 8, Spring Boot 2.7.18, explicit SQL through JdbcTemplate. No JPA/Hibernate.
- Use scripts/mvn.ps1 on Windows for Java 8 without changing global JAVA_HOME.
- Preserve config/application-local.properties and config/dev-accounts.properties
  (ignored, contain private settings and initial demo passwords).
  Never print or commit credentials. Keep local configuration outside the app JAR.
- No automatic schema changes at startup. V001-V008 are applied and immutable. Use future
  numbered migrations; never reset the populated development database.
- Ordinary tests must not require a database. Explicit read-only baseline checks use
  -Dtelecom.integration=true. Writable integration tests require a disposable database.
  scripts/Test-DaoIntegration.ps1 supplies an isolated temporary PostgreSQL instance
  for the milestone-2 DAO suite (-Dtelecom.dao.integration=true), never the dev database.
  scripts/Test-AuthIntegration.ps1 adds a separate scratch authentication database for
  -Dtelecom.auth.integration=true. Never reset dev data for auth tests.
- Ticket intake is in TicketWorkflowService/JdbcTicketWorkflowDAO. Keep ticket,
  initial history, customer notification and audit in one transaction. Resolve current
  profile links/roles from the database; never trust posted customer/actor IDs. See
  docs/TICKET_WORKFLOW.md. Future changes start at V009 and extend DatabaseMigrate.
- AssignmentService/JdbcAssignmentDAO implement desk-only recommendations and atomic
  assignment/reassignment; see docs/ASSIGNMENT.md. Preserve row_version predicates,
  current eligibility rechecks, stable engineer lock order and audit-only recording
  for same-status reassignments. Assignment must not set first_response_at.
- TicketTransitions is the central manual graph. LifecycleService/JdbcLifecycleDAO
  implement engineer acknowledgment/pending/resolution, desk closure, cancellation
  and one customer feedback submission. See docs/LIFECYCLE.md. Preserve current
  profile/assignment checks, first_response_at once, immutable metadata and atomic
  ticket/history/notification/audit writes. Never fabricate legacy seed resolutions.
- Dev login is password+CAPTCHA then OTP shown on the verification page in the same
  session (the /dev/otp inbox remains diagnostic). No SMTP. GET /login preserves a
  pending login; explicit restart is POST /login/restart with CSRF.
  Do not bypass the OTP stage or expose database errors/credential hashes to views.
- SLAService/SlaEvaluation calculate independent metrics; EscalationService and
  SlaOperationsService preserve atomic writes, ticket locks and row_version predicates.
  See docs/SLA_MONITORING.md. Keep UNIQUE(ticket_id,level), saved operational status,
  immutable escalation history and SYSTEM audit actors. Never reset escalation levels
  or original creation time on priority change/resumption. The web-only monitor is
  enabled by default, runs every 60 seconds and uses persisted work for restart recovery.
  Disable telecom.sla.monitor.enabled in writable MVC test contexts. No external email delivery.
- After each milestone or partial handoff update MILESTONES.md with implemented work,
  test evidence, actual migration state, limitations, and the precise next task.
- NotificationService/JdbcNotificationDAO and InAppNotificationDelivery implement
  recipient inbox/read state and transactional receipts with savepoint rollback,
  three attempts and safe failure codes. Desk is read-only processing oversight;
  managers share configured escalation-recipient messages. Engineer inbox requires
  current assignment. See docs/NOTIFICATIONS_EVENTS.md. No SMTP/SMS side effects.
- NetworkEventService/JdbcNetworkEventDAO persist desk commands. The dev-only simulator
  enqueues IDs only after commit. EventDispatcher uses a bounded queue; BackgroundJobs
  recovers persisted work and owns/shuts down consumers and scan executors. Preserve
  event/service uniqueness, node mapping locks, SYSTEM provenance and all-or-nothing
  fan-out through TicketWorkflowService's internal event method. Never fabricate a user.
  Disable telecom.jobs.enabled along with the SLA monitor in writable test contexts;
  CLI tools must never start background jobs. No synthetic live events are needed.
- Git was initialized but no author identity is configured; the first commit is pending.
- DashboardService provides current profile/assignment-scoped aggregates. Reports are
  desk/manager-only, owner/auth-version/role scoped and process-local. Preserve bounded
  Callable workers, row/byte/date limits, query timeout, expiry, CSV/TXT quoting and
  formula-prefix protection. See docs/REPORTS.md. No public export paths or arbitrary SQL.
- RawJdbcAssessment is an explicit scratch-only utility using TEMP tables, never a
  startup bean/HTTP endpoint. BoundedTicketAnalytics is an assessment example, not an
  unbounded production query. V008 expands resolution codes without invalidating old
  metadata. scripts/Verify-Delivery.ps1 tests a separate read-only/no-jobs JAR instance,
  generates synthetic exports and stops only its own process. Final tests: 214 passed,
  one opt-in skip, plus four live read-only checks and packaged smoke verification.
