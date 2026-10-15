# Dashboards and asynchronous reports

Milestone 9 completes all four role dashboards. Customer counts/recent tickets are
scoped to the current customer profile; engineer scope is current assignment. Desk
and manager see global operational totals, independent response/resolution SLA
counters, event failures and top-five engineer workloads. Averages include completed
resolution timestamps only. Today means Asia/Kolkata. Dashboard queries are current
read snapshots; successive cards may observe intervening updates.

Desk and manager dashboards link to /service-desk/reports and /manager/reports.
Choose a report, inclusive creation-date range and CSV/TXT. Submit with CSRF,
follow job status, refresh until COMPLETE, then download. Each user can access only
their own jobs, through the role path used to create them. Roles/auth version are
revalidated on submission, execution, status and download. Other users receive 404;
customer/engineer report access is forbidden. Delete/cancel is a CSRF-protected POST.

| Report | Definition |
|---|---|
| Ticket Volume | Ticket counts grouped by Kolkata creation day, priority and current status |
| SLA Compliance | Non-cancelled tickets: separate response/resolution completed, met, late and unfinished-overdue counts; met percentages use completed targets only |
| Engineer Performance | Counts, active workload, completed/on-time resolutions and average resolution minutes attributed to the CURRENT assigned engineer; UNASSIGNED is explicit |
| Incident Category | Category ticket counts and unresolved active counts |
| Regional Incident | Service-region total, critical and active ticket counts |
| Average Resolution | Per-priority completed counts/mean minutes, unresolved counts and unresolved-overdue counts; cancelled tickets excluded |
| Critical Incident | Critical ticket/service/region/category/status, UTC timestamps, deadlines and description |

Dates include both local dates: from 00:00 Asia/Kolkata through the next midnight
after the selected end date, exclusive. Supported years 2000-2100, maximum 366 days.
Each query reads ticket rows created in that window up to query execution time.
Report state/priority/assignment are current at execution; these are not historical
reconstructions of old ticket states. Completion at a deadline is on time. Blank
averages/percentages mean no completed denominator, not zero compliance. Unresolved
targets do not contribute to duration averages. Active overdue uses the injectable
Clock at generation; pending-customer time is not paused. Timestamps export as UTC
ISO instants. Aggregated creation days use Asia/Kolkata.

JdbcReportDAO executes parameterized, bounded SQL from ReportQueryFactory. Every
report is one PostgreSQL statement with a 20-second query timeout. No arbitrary
identifiers/SQL come from forms. There is no new schema migration for this milestone.
ReportService submits Callable/FutureTask jobs to two ExecutorService workers with
a queue of 16. Limits: three active jobs per user, 32 retained jobs overall, 5000
rows and 1 MiB UTF-8 per export. Saturation returns 429. Oversized/failed jobs have
a generic FAILED state; one failure does not stop later jobs. Completed/failed jobs
expire 30 minutes after creation and may be evicted earlier under capacity pressure.
Jobs and export bytes are process-local, cleared on restart, and must be regenerated
after restart or when accessing another app instance. Executors cancel/shut down on
context close; JDBC queries are also time-bounded. No public file paths are accepted.

CSV uses comma separators; TXT is quoted tab-separated text. Both quote every cell,
double embedded quotes and preserve commas, tabs and embedded newlines. Cells whose
first effective character is =, +, -, @ (including leading whitespace/BOM), or that
start with tab/newline, gain an apostrophe to protect spreadsheet opening. This may
also prefix negative numeric text. Downloads use fixed UUID filenames, attachments,
no-store and nosniff. Do not remove these protections when extending exports.

Verification uses disposable PostgreSQL fixtures with known met/late/open/cancelled
targets, exact Kolkata midnight boundaries, CSV formula/multiline content, all seven
SQL shapes, four scoped dashboards, cross-user/role jobs and CSRF. Unit tests cover
encoding, limits, cancellation, expiry, capacity and failure isolation.

For a local demonstration, restart TelecomOSS Dev, sign in as desk.demo or manager.demo,
open Generate reports and select a creation range containing your development tickets.
Use CSV first, then TXT. No migration or credential change is needed. Sample exports
under docs/samples contain synthetic data only; see docs/ASSESSMENT.md.
