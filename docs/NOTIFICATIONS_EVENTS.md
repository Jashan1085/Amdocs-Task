# Notifications and network events

Milestone 8 adds local in-app notification delivery and a development network event
simulator. Apply V007 with scripts/Update-Database.ps1, then restart TelecomOSS Dev.
V001-V006 remain unchanged. No migrations run during normal application startup.

## Notification inbox and delivery

Dashboard links open /customer/notifications, /engineer/notifications,
/manager/notifications or /service-desk/notifications. Lists use 20-row pages and an
unread filter. Customers see their own profile's records. Engineers see recipient
records only while they remain the ticket's current assigned engineer. Managers
share an oversight inbox for configured escalation recipients; no extra login roles
were introduced. Desk sees a read-only processing overview across recipients.

Mark read requires the recipient role, current database profile, ownership and CSRF.
Repeated marking preserves the first read_at. Desk cannot mark other recipients'
messages. Read state is independent of delivery_status: reading a pending message
does not publish it, change its attempts or suppress its eventual delivery.

The web-only notification task starts after five seconds and scans up to 100 due
records every five seconds. Delivery means a durable IN_APP notification_receipt;
it does not send email/SMS. Inbox records show pending, delivered and failed states
for review. Existing configured escalation email contacts are not contacted.

Each attempt locks one record with FOR UPDATE SKIP LOCKED, increments attempts,
and publishes its unique receipt before marking DELIVERED in the same transaction.
A nested JDBC savepoint rolls back a failed delivery, including SQL transaction
errors and partially inserted receipts. The outer transaction persists the retry
result. Failures wait at least 60 seconds; after three attempts the row is FAILED
with a safe DELIVERY_UNAVAILABLE code. Raw exceptions and credentials are not shown.
Read state is preserved throughout. FAILED notifications stay visible for desk review;
there is no automatic reset or manual retry action in this milestone.

An interrupted uncommitted attempt rolls back. PENDING and legacy PROCESSING rows
are recovered from PostgreSQL on subsequent scans. The unique receipt key makes
replay idempotent. NotificationDelivery currently requires transactional, idempotent
local delivery; replacing it with external SMTP requires separate delivery guarantees
and configuration, rather than assuming an email can roll back with JDBC.

## Local demonstration

1. Restart **TelecomOSS Dev** in IntelliJ, sign in with existing credentials and the
   normal CAPTCHA/OTP flow. Customer ticket creation or lifecycle changes generate
   notifications. Open the customer inbox; refresh after five seconds to see delivery.
   Mark a message read and try the unread filter.
2. Sign in as desk.demo. Open **Network events and simulator**, then **Manage nodes
   and mappings**. Register a node, for example DEMO-WEST, with name West access node
   and region West. The form redirects to its mappings and shows its numeric node ID.
3. Link an existing service code such as SVC-MOB-001. Link a second active service to
   demonstrate one event creating multiple tickets. Links are idempotent. A service
   may depend on multiple nodes; region is descriptive, not an eligibility restriction.
4. Return to network events and choose **Simulate an event**. Enter the node ID,
   OUTAGE or DEGRADATION and a description. This persists a real development event,
   then queues its ID. OUTAGE creates NETWORK_OUTAGE/CRITICAL tickets;
   DEGRADATION creates SLOW_DATA/HIGH tickets. Severity matches priority.
5. Refresh event detail to see status and ticket links. Each ticket has independent
   latest-policy SLA deadlines, initial OPEN history, customer notification and
   SYSTEM audit with the event reference. The event's original submitting user is
   recorded separately; the background worker never impersonates that user.
6. Re-submitting the same form uses its UUID event key and returns the same event.
   A key reused with changed node/type/description/actor returns a conflict. New form
   loads intentionally create a new event key and may create new incident tickets.
7. Simulate against an empty node to see UNMAPPED. Link an eligible active service,
   then select **Retry after review** on event detail. Inactive customers/services
   are skipped; if none are eligible the event remains UNMAPPED. PROCESSED events
   cannot be manually reset. Failed events can also be retried after desk review.

Simulator GET/POST routes exist only in the dev profile. Network node/mapping and
event review routes are service-desk only. All mutations use CSRF and reload current
roles/profile state. No external network monitoring or simulator credentials are used.
Node creation and additive service mapping are supported; editing, unlinking and node
deactivation are deliberately outside these initial screens.

## Processing, recovery and handoff

V007 adds network_node, service_network_node, network_event and event_ticket, plus
notification retry fields and immutable notification_receipt. Event keys are unique;
event_ticket has primary key (event_id,service_id), unique ticket_id and a composite
foreign key ensuring the associated ticket belongs to that service. Associations
and receipts reject UPDATE/DELETE. No demo nodes, mappings or events are installed
automatically on the normal development database.

NetworkEventController/NetworkSimulatorController call NetworkEventService to persist
commands. Enqueue happens after transaction commit. EventDispatcher uses a bounded
ArrayBlockingQueue of 256 IDs and an in-flight set. Full queues leave work persisted;
the recovery task scans due PENDING/PROCESSING events on startup and every 15 seconds.
Two ExecutorService consumers process IDs independently; errors in one job do not
terminate other consumers. Each application instance rechecks due status under an
event FOR UPDATE SKIP LOCKED lock, so overlap/replay cannot duplicate tickets.

NetworkEventService locks the event, takes a shared node lock and reads service IDs
in ascending pages of 50. Mapping commands take an exclusive node lock, stabilizing
the node's mappings during processing. TicketWorkflowService's internal event method
requires the worker transaction and reuses existing service/customer locks, active
checks, sequence numbers, SLA targets and ticket insertion. There is no public
system-ticket endpoint or fake authenticated account. All tickets for one event,
associations, initial histories, notifications and audits commit together. On failure
a savepoint rolls back every partial business write; attempts and a safe error code
survive in the outer transaction. Retry delay is 60 seconds, maximum three attempts
per cycle. Explicit desk retry increments retry_count and starts a new bounded cycle.
Large node fan-out stays one atomic transaction, although service reads are paged.

BackgroundJobs owns the event consumers and scheduled scan pool. It starts once at
ApplicationReadyEvent and interrupts/awaits executor shutdown on context close.
Set telecom.jobs.enabled=false for diagnostics; queued work remains persisted for
the next enabled restart. CLI migration/read-only checks never start these jobs.
Isolated integration tests disable both this property and telecom.sla.monitor.enabled.

```powershell
.\scripts\mvn.ps1 test
.\scripts\Test-AuthIntegration.ps1
.\scripts\mvn.ps1 '-Dtelecom.integration=true' '-Dtest=DatabaseSchemaIntegrationTest' test
```

The full harness uses disposable PostgreSQL databases. Tests cover delivery/read
independence, recipient isolation, retries/savepoints, receipts, overlap, queue bounds,
failure isolation, startup recovery, fan-out/replay, unmapped review, transactional
ticket failures, role/CSRF validation and executor lifecycle. The separate configured
database suite is read-only. See MILESTONES.md for actual results and migration state.

Next scope is milestone 9: four completed dashboards and seven asynchronous CSV/TXT
reports with authorized job status/downloads. No reports were implemented here.
