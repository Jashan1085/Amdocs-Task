# SLA monitoring and escalation

Milestone 7 uses Java 8, explicit JdbcTemplate SQL and Spring JDBC transactions.
Apply V006 explicitly with scripts/Update-Database.ps1 before restarting the app.
No schema changes run during normal startup. V001-V005 remain immutable.

## Evaluation rules

Response and resolution are separate clocks starting at ticket creation. The stored
priority/policy version determines deadlines and warning_percent (initially 80%).
PENDING_CUSTOMER does not pause time. At the warning instant a target is AT_RISK;
at its deadline an unfinished target is BREACHED. Completion at the deadline is MET;
completion after it is BREACHED. Completed metrics retain their result without
triggering new escalation. Cancelled work is NOT_APPLICABLE. Resolved, closed and
cancelled tickets are excluded from the active monitor and counters.

| Level | Recipient | Trigger on unfinished work |
|---|---|---|
| 1 | Team Lead | Either target reaches its warning threshold |
| 2 | Network Manager | Either target reaches its deadline |
| 3 | Operations Manager | Resolution deadline plus one full resolution SLA duration |

Escalation levels are cumulative: the first check of an already overdue ticket
records all due levels atomically at the actual detection time. There is one entry
per ticket/level for its lifetime. Priority changes and resumption do not reset levels.
Changing priority can immediately make the ticket overdue.

## Screens and manual verification

1. Restart **TelecomOSS Dev** in IntelliJ after applying the migration. Sign in with
   the existing password, displayed CAPTCHA and development OTP flow.
2. As desk.demo, follow **SLA monitoring** from the dashboard or visit
   /service-desk/sla. Status/priority filters and paging show independent response and
   resolution results. Counters cover all active tickets, independent of list filters.
3. Open an unresolved ticket and select **Change priority**. Supply a different
   priority and reason. The latest immutable policy for that priority is selected;
   deadlines are recalculated from ORIGINAL creation time. History has no artificial
   status change; audit records old/new priority and policy versions.
4. Wait up to 60 seconds for a due check (also 60 seconds after startup). A due ticket
   becomes ESCALATED; detail shows its highest level, recorded operational status and
   escalation history. Seed dates are fixed, so active seed tickets may already be
   overdue when you restart. Do not change the workstation clock or edit live rows to
   test boundaries; the disposable tests use an injected Clock.
5. As desk.demo, use **Resume operational work**, with a reason, to restore exactly
   the stored pre-escalation state. No acknowledgment or deadline resets. The engineer
   can then continue the normal lifecycle. A later, previously unrecorded level may
   escalate it again. Existing levels do not repeat on the next scan.
6. As manager.demo, open /manager/sla for the read-only SLA summary. Customer and
   engineer ticket details show SLA data only after normal ownership/assignment checks.

Only service desk changes priority or resumes escalation. All mutation forms require
CSRF, a current row_version and a reason. Stale forms return 409. Escalated tickets
cannot bypass explicit resumption through ordinary engineer actions; desk cancellation
remains available. Terminal tickets cannot change priority or resume escalation.
Unknown legacy previous status blocks resumption instead of guessing. V006 adopts
only explicit prior operational states from existing escalation status-history rows.

## Persistence and concurrency

- V006 adds escalation_recipient, ticket_escalation_state and append-only
  escalation_history, plus escalation notification recipients and explicit SYSTEM
  audit actors. USER audit actors still require an existing user ID.
- Configured contacts initially use example.test addresses. No new login roles,
  email sending, inbox or dispatch were added. Notification delivery is milestone 8.
  Customer, current engineer (if assigned) and configured level recipient each receive
  a durable PENDING notification. Disabled/missing contacts fail the transaction and
  retry on a later scan. Configure real contacts before implementing delivery.
- EscalationService locks the ticket before assessing current state. Ticket status,
  version, saved operational state, escalation records, notifications and audit are
  committed together. Database UNIQUE(ticket_id,escalation_level) and per-recipient
  notification indexes back the locked recheck against overlapping app instances.
- Each new escalation batch increments row_version, even if already ESCALATED.
  Status history records only actual status changes; further levels have their own
  escalation history and SYSTEM audit entries.
- Desk mutations reload current role/profile and lock actor, ticket and owner rows.
  Priority changes and resumption include their notification/audit writes atomically.

## Entrypoints and tests

SlaEvaluation is the pure Clock-based calculation; SLAService loads versioned policy.
JdbcSlaDAO contains explicit SQL. EscalationService performs one transactional check;
SlaMonitor reads active tickets in keyset pages of 200 and processes a synchronized
PriorityQueue ordered CRITICAL, HIGH, MEDIUM, LOW, then ticket ID. Priority changes
during scanning can defer a ticket until the next scan. No full-table Java queue is
retained. Each check uses a five-second lock timeout; one failed job does not stop
remaining tickets. Every run scans persisted active work, providing restart recovery.

SlaMonitorConfiguration creates a daemon ScheduledExecutorService only for web
applications when telecom.sla.monitor.enabled=true (default). SlaScheduler starts
once on ApplicationReadyEvent, uses a 60-second fixed delay and shuts down cleanly.
Set telecom.sla.monitor.enabled=false for local UI-only diagnostics. There is no
public endpoint to trigger the monitor. CLI tools and read-only tests never start it.

```powershell
.\scripts\mvn.ps1 test
.\scripts\Test-AuthIntegration.ps1
.\scripts\mvn.ps1 '-Dtelecom.integration=true' '-Dtest=DatabaseSchemaIntegrationTest' test
```

The full harness uses disposable PostgreSQL databases, never live development rows.
Tests cover exact warning/deadline/extra-duration boundaries, completed/cancelled work,
original-creation priority changes/latest policy, saved-status resumption, role/CSRF,
stale versions, rollback, overlapping checks, restart-style catch-up, duplicate
prevention, missing recipients, SQL counters, queue ordering and executor shutdown.
The separate schema test only reads the configured development database.

Next scope: milestone 8 inbox/delivery/retries and network event simulation/recovery.
