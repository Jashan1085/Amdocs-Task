# Milestone 5: engineer assignment

Restart **TelecomOSS Dev**, sign in as `desk.demo`, and open **Assignment queue**.
It initially filters OPEN tickets; status/priority filters and pagination remain
available. Open a ticket and select **Assign engineer**. Choose one of the recommended
engineers, add an optional reason, and save. The detail page shows ASSIGNED and the
engineer's name/code. Customers can see the assigned engineer on their own tickets.

For an assigned ticket, use **Reassign engineer**, choose a different engineer and
provide a reason. The current engineer is excluded from recommendations. If no
eligible alternative exists, the page states that explicitly and has no save form.
The current demo dataset has one available engineer per some skill/region pairs, so
no alternative is a valid outcome. Do not change real availability simply to force
the demonstration. Example initial match: the West NETWORK_OUTAGE seed ticket matches
ENG1008 (Core Network). West BROADBAND incidents match ENG1021 (Broadband).

## Rules

- Exact specialization and service-region match; engineer must be available.
- Up to three recommendations, ordered by active workload ascending, experience
  descending, employee code ascending (UTF-8/C collation for deterministic ties).
- Workload is derived from assigned tickets except RESOLVED, CLOSED and CANCELLED;
  no mutable counter or workload capacity is introduced.
- OPEN becomes ASSIGNED; ASSIGNED, IN_PROGRESS and PENDING_CUSTOMER retain their
  status during reassignment. Resolved/closed/cancelled tickets cannot be assigned.
  ESCALATED is deferred until milestone 7 defines operational-state resumption.
- Assignment does not acknowledge a ticket, reset deadlines, change SLA policy or
  clear response/resolution timestamps. Acknowledgment belongs to milestone 6.
- Recommendations are advisory snapshots. Save rechecks current role/account,
  ticket version/state, category rule, service region and engineer eligibility.
  An eligible engineer need not still rank in the displayed top three at save time.
- A stale form yields HTTP 409: reload the ticket and try again. Reassigning the
  same engineer and missing reassignment reasons are rejected without writes.

Default category rules are in additive V004 category_specialization:

| Category | Specialization |
|---|---|
| NETWORK_OUTAGE, SIM_ISSUE, ROAMING | Core Network |
| CALL_DROP, SLOW_DATA | RAN |
| NO_CONNECTIVITY, ENTERPRISE_LINK, OTHER | IP Network |
| BROADBAND | Broadband |
| BILLING | Billing |

They are initial configurable rules rather than automatic changes to engineer data.
There is no rule-management UI yet. Missing mappings produce no recommendations;
save fails safely. Engineer login is optional for eligibility; the engineer record's
availability, skill and region determine matching.

## Persistence and concurrency

V004 extends DatabaseMigrate's known checksum chain. Apply with
`scripts/Update-Database.ps1` without reprovisioning accounts. V001-V003 remain
immutable. No automatic migrations run at normal startup.

Notification now targets exactly one customer or engineer; existing customer rows
are preserved. Successful assignment/reassignment records two PENDING notifications:
one for the customer and one for the new engineer. Delivery/inbox processing remains
milestone 8. Audit adds previous/new engineer foreign keys and a reason. Existing
creation audit rows remain unchanged and the append-only guard stays enabled.

AssignmentService uses one JDBC transaction. It locks the current desk actor, then
the ticket; checks submitted row_version; shares locks on service/customer and the
category rule; and locks old/new engineers in ascending ID order. That order avoids
deadlocks when two tickets swap engineers. An UPDATE with row_version predicate
increments the version once. OPEN-to-ASSIGNED adds status history; reassignment
records engineer changes only in audit, avoiding forbidden same-status history.
Ticket update, history (when applicable), both notifications and audit commit together.

Entry points: AssignmentService; JdbcAssignmentDAO; EngineerRanking;
AssignmentController GET/POST `/service-desk/tickets/{id}/assignment`;
TicketController `/service-desk/queue`; templates/tickets/assignment.html.

## Verification

Run `scripts/Test-AuthIntegration.ps1` for unit and disposable PostgreSQL integration
tests, including sorting/ties/top-three, skill/region/availability, actual active
workload, no-match, role/CSRF denial, eligibility changes, preserved SLA/status,
initial versus reassignment history, rollback, competing edits and engineer swaps.

Read-only live checks:
`scripts/mvn.ps1 -Dtelecom.integration=true -Dtest=DatabaseSchemaIntegrationTest test`.
These verify installed checksums, baseline history/SLA consistency, category rules
and notification recipient integrity. Tests never reset the development database.

Milestone 6 manual lifecycle is implemented in LIFECYCLE.md. It preserves assignment
locks, optimistic versions, current ownership checks and atomic related writes.
TicketTransitions supplies the central manual graph; assignment uses OPEN-to-ASSIGNED.
Next is milestone 7 SLA monitoring/escalation.
