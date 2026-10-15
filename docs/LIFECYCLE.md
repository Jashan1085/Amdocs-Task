# Milestone 6: manual ticket lifecycle

Restart **TelecomOSS Dev** after migration. For the full demo, use a newly created
ticket so that resolution data is recorded by the new workflow:

1. `customer.demo`: raise a NETWORK_OUTAGE ticket for SVC-MOB-001 (West).
2. `desk.demo`: open Assignment queue, select the ticket, assign ENG1008.
3. `engineer.demo`: open **View assigned tickets**, open the ticket and choose
   **Acknowledge / start work**. Its status becomes IN_PROGRESS.
4. Optionally choose **Request customer information** with a reason, then **Resume
   investigation** after information arrives. The SLA clock continues.
5. Choose **Resolve ticket**; supply root cause, resolution details and a code.
6. `desk.demo`: open the resolved ticket and choose **Close ticket**.
7. `customer.demo`: open the closed ticket, choose **Leave feedback**, select a
   rating from 1 (poor) to 5 (excellent), optionally add comments and submit once.

Use separate browser profiles or sign out between roles. Development OTP is shown
on the verification page. Other engineer records may not have login accounts; the
ENG1008 profile is linked to engineer.demo for this demonstration.

## Actions and permissions

| Actor | Action | Permitted source and resulting status |
|---|---|---|
| Desk | Assign | OPEN → ASSIGNED (milestone 5) |
| Current assigned engineer | Acknowledge | ASSIGNED → IN_PROGRESS |
| Current assigned engineer | Request customer information | IN_PROGRESS → PENDING_CUSTOMER; reason required |
| Current assigned engineer | Resume | PENDING_CUSTOMER → IN_PROGRESS |
| Current assigned engineer | Resolve | IN_PROGRESS → RESOLVED; resolution fields required |
| Desk | Close | RESOLVED → CLOSED; recorded resolution required |
| Owning customer | Cancel | Own OPEN → CANCELLED; reason required |
| Desk | Cancel | OPEN/ASSIGNED/IN_PROGRESS/PENDING_CUSTOMER/ESCALATED → CANCELLED; reason required |
| Owning customer | Feedback | CLOSED only, one submission; status remains CLOSED |

No reopening or cancellation of resolved/closed tickets is supported. Escalated
operational resumption remains milestone 7. Requesting/resuming customer information
is performed by the assigned engineer in this version; it does not pause SLA time.

All actions use current database role/profile/assignment/ownership, not submitted
actor/customer/engineer IDs. Reassignment immediately removes the former engineer's
read/write access. Engineer views also filter by current profile ID. Acknowledgment
sets first_response_at once; subsequent resumption or reassignment preserves it.
Resolution and closure set their own timestamps. Deadlines and SLA version never
change in these actions.

## Persistence and concurrency

V005 adds append-only ticket_resolution, ticket_cancellation and ticket_feedback.
Each uses ticket_id as its primary key, with appropriate actor/profile foreign keys.
Resolution stores root cause, text, controlled code, resolver and time. Cancellation
stores reason, actor and time. Feedback stores owning customer, rating, comments
and time. Existing tickets and histories are not rewritten or backfilled.

The historical RESOLVED seed fixture has no resolution record. It remains readable
but closure is blocked with an explicit message until reviewed resolution data is
provided through a future adoption/backfill procedure. Do not invent a root cause
or resolver to make it closable. Previously closed tickets remain eligible for
feedback according to ownership, independently of legacy resolution storage.

LifecycleService locks actor, ticket, service/customer and (for engineer operations)
the current engineer profile. It checks the submitted row_version and permissions,
then changes ticket timestamps/status, inserts required metadata and status history,
customer/assigned-engineer notifications and audit in one JDBC transaction. Failures
roll back every write. Feedback increments row_version but adds no status history;
the ticket primary key and row lock prevent duplicate submissions. Stale edits return
HTTP 409, foreign ticket access 404, and wrong role access 403.

Notifications remain durable PENDING records. No email/SMS or background dispatch
is introduced. V005 adds FEEDBACK_RECEIVED to the controlled notification events.
Description, reasons, resolution text and comments are escaped by Thymeleaf. All
mutation forms require CSRF. Resolution/cancellation/feedback reject UPDATE/DELETE.

## Entry points and verification

- TicketTransitions: central manual graph and role/action policy.
- LifecycleService/JdbcLifecycleDAO: authorized transitions, metadata, feedback and
  atomic related writes. DAO metadata reads return immutable maps for view fields.
- LifecycleController: GET/POST `{scope}/tickets/{id}/actions`, scope is customer,
  service-desk or engineer; action is a controlled TicketAction. Feedback GET/POST
  is `/customer/tickets/{id}/feedback`.
- TicketController: `/engineer/tickets` and authorized engineer detail/history,
  alongside existing customer/desk routes. All lists support filters/paging.
- templates/tickets/action.html, feedback.html and lifecycle.html: forms/details.

Run `scripts/Test-AuthIntegration.ps1` for disposable PostgreSQL tests. They include
the complete web flow, exhaustive graph checks, owner/engineer/role/CSRF permissions,
stale versions, immutable/required metadata, first-response preservation, feedback
limits/concurrency, legacy closure refusal and rollback at each write stage.

Apply pending migrations with `scripts/Update-Database.ps1` (no -DevAccounts when
already provisioned). No startup migrations. Read-only live verification is:
`scripts/mvn.ps1 -Dtelecom.integration=true -Dtest=DatabaseSchemaIntegrationTest test`.

Next milestone is SLA monitoring/escalation, not notification delivery or reports.
