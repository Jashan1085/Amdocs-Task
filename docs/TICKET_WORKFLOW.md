# Milestone 4: customer ticket intake and tracking

Restart **TelecomOSS Dev** after applying migrations. The Customer workspace links
to **Services / raise a ticket** and **View tickets**. Select an active service,
choose category, priority and severity, describe the issue, then create the ticket.
The detail page shows its number, status, separate response/resolution deadlines
and chronological status history. Lists offer status/priority filters and pages of
20 rows. Display times use Asia/Kolkata (IST); persistence uses UTC instants.

For Service Desk, open Services, enter an exact customer number (for example
CUST1001), and choose that customer's service. The desk can also list/detail tickets.
Milestone 5 adds assignment/reassignment; see ASSIGNMENT.md. Manual transitions,
cancellation and feedback are implemented in milestone 6; see LIFECYCLE.md.

## Persistence and authorization

Apply additive V003 explicitly with `scripts/Update-Database.ps1` (without
`-DevAccounts` for already provisioned demo users). V004 builds on this for assignment;
the runner applies all pending versions. Normal startup never migrates.
V001/V002 remain unchanged. The migration runner verifies a contiguous checksum
ledger before DDL and applies pending versions in one transaction under an advisory
lock. Repeating it is a no-op. Never rerun initial installation on a populated DB.

`TicketWorkflowService` derives the actor from the authenticated principal, reloads
current account roles/profile links/version/lock state, and checks service ownership.
No submitted actor/customer ID is trusted. Cross-customer detail, history and creation
are rejected. Service-desk scope requires its current database role, including for
direct service calls. Creation locks the actor row and shares locks on service and
customer rows while validating their active status. Service ownership transfers are
outside the approved model.

Creation writes all of these in one Spring-managed JDBC transaction:

1. OPEN ticket, unassigned, using a database sequence number such as TT-0000000001.
2. Initial NULL-to-OPEN history, actor `user:<app_user ID>`.
3. Customer notification with TICKET_CREATED event and PENDING delivery state.
4. Append-only audit event with actor user foreign key and ticket foreign key.

Numbers intentionally use a distinct format from the historic TT-2026-000001 seed
fixtures. Sequence gaps are expected after rollbacks; never use MAX + 1. Notification
recipients are customer IDs, so desk intake works even without a customer login.
Read state is separate from delivery processing state. Inbox display, background
delivery and retries use the in-app processor described in [NOTIFICATIONS_EVENTS.md](NOTIFICATIONS_EVENTS.md); a PENDING record does not mean delivery succeeded.

SLAService selects the highest immutable policy version for the chosen priority and
uses the injectable Clock once for creation and both deadlines. Durations are
continuous minutes. Seed policies: CRITICAL 15/120, HIGH 30/240, MEDIUM 120/720,
LOW 480/2880 response/resolution minutes. Missing policies fail without partial writes.

Initial intake permits customers and desk to select the initial priority/severity.
Only active customers/services accept new tickets; older tickets remain readable.
Descriptions are nonblank, capped at 4000 characters, and escaped by Thymeleaf.
Requests use CSRF, bound SQL parameters and controlled enum values. Repeat form
submissions create separate tickets; idempotency keys are not part of this milestone.

## Entry points and verification

- TicketController: /customer and /service-desk services, tickets, tickets/new,
  POST tickets and tickets/{id}. Desk services accept customerNumber; lists accept
  zero-based page (default 0), and ticket lists accept status/priority.
- TicketWorkflowService: ownership, current actor, active-state rules and transaction.
- JdbcTicketWorkflowDAO: filtered reads, service/customer locking and related writes.
- SLAService: immutable policy selection and deadlines.
- V003: ticket_number_seq, notification, audit_event, indexes and audit mutation guard.

Run `scripts/Test-AuthIntegration.ps1` for isolated PostgreSQL security, UI, workflow
and DAO tests. It installs V001 fixtures and upgrades through V008 in its disposable
auth DB; the separate DAO DB remains a baseline-only mapping test. Tests include all four roles,
cross-customer denial, escaped user content, filters/paging, concurrent numbers,
policy selection and rollback on history, notification or audit failure.
