# Assessment evidence and requirement map

This is a Java 8 / Spring Boot 2.7.18 local implementation of the supplied Telecom
Service Assurance case study. The approved milestone plan governs implementation;
the source document is reference material. No JPA/Hibernate is used. The eight exact
case-study resolution codes are supported by V008; five earlier resolution codes
remain valid for historical compatibility. The SLA UI also shows signed remaining
seconds, frozen at completion. ON_TRACK corresponds to the case study's WITHIN_SLA;
MET denotes completed-on-time, and cancelled targets are NOT_APPLICABLE.

## Requirement-to-code/test evidence

| Requirement | Concrete implementation | Verification |
|---|---|---|
| Classes, objects, encapsulation, enums, collections, generics | Immutable model snapshots, controlled enums, ResultPage<T>, bounded queues/maps | ReadDAOIntegrationTest and workflow tests |
| Inheritance, abstraction and polymorphism | WorkflowException extends RuntimeException; TelecomPrincipal implements UserDetails; OtpDelivery/NotificationDelivery contracts dispatched through injected implementations | AuthenticationIntegrationTest, BackgroundJobsTest |
| Lambdas, functional interfaces, method references | OtpDelivery fallback lambda, TicketMetric, ReportFormatter; ranking comparators and collectors | AssessmentTest, ReportEncoderTest, ranking tests |
| Default/static interface methods | OtpDelivery defaults; TicketMetric.units() and resolutionMinutes() | AssessmentTest and authentication tests |
| Streams: status, priority, region, workload, SLA breaches, averages, categories, engineer performance, repeat customers | assessment/BoundedTicketAnalytics, capped at 1000 explicit snapshots; no unbounded database materialization | AssessmentTest known totals and empty/bound checks |
| Stream recommendation, Comparator, Optional | EngineerRanking and AssignmentService; top three skill/region/availability matches | EngineerRankingTest and assignment integration cases |
| Connection, PreparedStatement, ResultSet, batch | RawJdbcAssessment on scratch-only temporary tables; application DAOs use parameterized Spring JDBC | rawJdbcAssessmentCommitsRollsBackAndLeavesBusinessTablesUntouched |
| Commit, rollback, Savepoint | RawJdbcAssessment explicit commit, invalid-row savepoint recovery and full rollback; normal services use Spring JDBC transactions | AssessmentTest guard, raw JDBC integration and workflow rollback/concurrency tests |
| SELECT/INSERT/UPDATE/DELETE | Temporary customer/incident CRUD with bound values; no permanent assessment data | RawJdbcAssessment known result |
| JOIN/LEFT JOIN/GROUP BY/HAVING/ORDER BY/subquery/CASE/aggregates/date functions | RawJdbcAssessment aggregate query plus report SQL and assignment joins | Raw JDBC evidence and seven report SQL integration shapes |
| Runnable, ExecutorService, BlockingQueue, synchronization | EventDispatcher/BackgroundJobs, bounded ArrayBlockingQueue and in-flight set | BackgroundJobsTest and persisted event recovery/overlap tests |
| ScheduledExecutorService/PriorityQueue | SlaScheduler, SlaMonitor and synchronized EscalationQueue | SlaMonitorTest, SlaEvaluationTest, escalation boundary/overlap tests |
| Callable/Future | ReportService uses Callable<Void>/FutureTask with bounded pool and queue | ReportServiceTest and authorized report web tests |
| CAPTCHA, password hashing, OTP, lockout, history, role/CSRF | BCrypt, per-form CAPTCHA IDs, session-bound OTP, database login history and current-role checks | Full authentication/security integration suite; no production OTP logging |
| Assignment/lifecycle atomicity | Lock/recheck/version predicates and single transactions across ticket/history/notification/audit/metadata | Assignment and lifecycle failure-injection/concurrency tests |
| Notifications and automatic incidents | Durable local receipts, bounded retries, saved events and unique event/service ticket associations | Delivery/retry/SQL-savepoint/replay/multi-service rollback tests |
| Seven asynchronous CSV/TXT reports | ReportQueryFactory, JdbcReportDAO, ReportService, ReportEncoder | Known independent SLA/duration totals, date boundaries, job ownership and export tests |
| File handling/config/logging | UTF-8 byte exports, SampleExports Files.write, external ignored properties, generic SLF4J worker warnings | Encoder tests, generated synthetic samples, package/config inspection |
| DAO pattern | dao interfaces and explicit Jdbc*DAO implementations separate persistence from services | DAO integration mapping and SQL tests |
| Factory pattern | ReportFormatterFactory creates CSV/TXT formatting strategies; ReportQueryFactory chooses closed SQL shapes | Both format encoder tests and all report queries |
| Strategy pattern | ReportFormatter, OtpDelivery, NotificationDelivery implementations | Real format selection, OTP delivery adapters and delivery failure tests |
| Observer-style lifecycle | ApplicationReadyEvent listeners start workers once; DisposableBean closes owned executors | BackgroundJobsTest and SlaMonitorTest |

This matrix is requirement/test evidence, not a claimed line/branch coverage percentage.
No numeric coverage instrumenter was added. Exact final run counts and local migration
state are recorded in MILESTONES.md, separately from historical milestone runs.

## Raw JDBC exercise safety and reproduction

RawJdbcAssessment is a plain explicit utility, not a bean, startup runner or HTTP
endpoint. It refuses connection metadata URLs except the harness's loopback
telecom_oss_auth_test / telecom_oss_dao_test scratch names, before executing SQL.
Its two TEMP tables use ON COMMIT DROP. An invalid CHECK insert is rolled back to a
Savepoint without losing prior batch inserts; prepared UPDATE/DELETE and aggregate
queries then run and commit. A second temporary probe is fully rolled back and its
absence verified. try-with-resources closes Connection, PreparedStatement and
ResultSet. Exceptions trigger full rollback. It never modifies application tables.

Run .\scripts\Test-AuthIntegration.ps1. The raw JDBC test checks known totals and
unchanged business ticket/history counts. Ordinary unit tests also prove that the
utility refuses the normal development URL without running SQL. Do not rename a
production database to bypass this deliberately narrow educational guard.

## Synthetic exports

docs/samples/synthetic-incidents.csv and .txt are offline fixtures generated by the
same formatter used for authorized downloads. They demonstrate quotes, commas,
embedded newlines and a formula prefix safely escaped with an apostrophe. They are
not exported customer records or real report totals. After compiling, regenerate with:

```powershell
java -cp target/classes com.amdocs.telecom.assessment.SampleExports docs/samples
```

The integrated finalRehearsalRunsEventToResolutionFeedbackDeliveryAndAuthorizedReport
test simulates an outage through the MVC form, deterministically invokes the background
event processor, assigns through the desk form, acknowledges/resolves through engineer
forms, closes through the desk form, submits customer feedback, processes/reads a local
notification, and downloads the manager's authorized critical report. It uses real
PostgreSQL in a freshly installed scratch database, not development tickets. Other
tests cover the asynchronous schedulers/queues separately with controlled execution.
