# Database design and ER diagrams

[Design index](README.md) · [Class diagrams](CLASS_DIAGRAMS.md) · [Database setup](../../db/README.md)

## Contents

1. [Core ticket and SLA relationships](#core-ticket-and-sla-relationships)
2. [Authentication and audit](#authentication-and-audit)
3. [Network events and notifications](#network-events-and-notifications)
4. [Integrity and indexing](#integrity-and-indexing)
5. [Migration inventory](#migration-inventory)

The diagrams use physical table names and selected keys/fields. They are split by
concern for readability; repeated entities refer to the same tables. The complete
DDL lives in [versioned migrations](../../db/migrations/).

## Core ticket and SLA relationships

```mermaid
erDiagram
    customer ||--o{ telecom_service : owns
    telecom_service ||--o{ trouble_ticket : receives
    network_engineer o|--o{ trouble_ticket : assigned_to
    sla_configuration ||--o{ trouble_ticket : governs
    trouble_ticket ||--o{ ticket_status_history : records
    trouble_ticket ||--o| ticket_resolution : resolves
    trouble_ticket ||--o| ticket_cancellation : cancels
    trouble_ticket ||--o| ticket_feedback : receives
    customer ||--o{ ticket_feedback : submits
    network_engineer ||--o{ ticket_resolution : resolves
    trouble_ticket ||--o| ticket_escalation_state : tracks
    trouble_ticket ||--o{ escalation_history : escalates
    escalation_recipient ||--o{ escalation_history : receives_level

    customer {
        bigint customer_id PK
        varchar customer_number UK
        varchar status
    }
    telecom_service {
        bigint service_id PK
        bigint customer_id FK
        varchar service_code UK
        service_type service_type
        varchar region
    }
    network_engineer {
        bigint engineer_id PK
        varchar employee_code UK
        varchar specialization
        boolean available
    }
    sla_configuration {
        ticket_priority priority PK
        integer policy_version PK
        integer response_minutes
        integer resolution_minutes
        smallint warning_percent
    }
    trouble_ticket {
        bigint ticket_id PK
        varchar ticket_number UK
        bigint service_id FK
        bigint assigned_engineer_id FK
        ticket_priority priority FK
        integer sla_policy_version FK
        ticket_status status
        timestamptz response_deadline
        timestamptz resolution_deadline
        bigint row_version
    }
    ticket_status_history {
        bigint history_id PK
        bigint ticket_id FK
        ticket_status old_status
        ticket_status new_status
        varchar changed_by
    }
    ticket_resolution {
        bigint ticket_id PK, FK
        bigint resolver_engineer_id FK
        varchar resolution_code
    }
    ticket_cancellation {
        bigint ticket_id PK, FK
        bigint cancelled_by_user_id FK
        varchar reason
    }
    ticket_feedback {
        bigint ticket_id PK, FK
        bigint customer_id FK
        smallint rating
    }
    ticket_escalation_state {
        bigint ticket_id PK, FK
        smallint highest_level
        ticket_status previous_operational_status
    }
    escalation_history {
        bigint escalation_id PK
        bigint ticket_id FK
        smallint escalation_level FK
        varchar response_state
        varchar resolution_state
    }
    escalation_recipient {
        smallint escalation_level PK
        varchar recipient_name
        boolean enabled
    }
```

- A ticket belongs to one service; its customer is derived through that service.
- Engineer assignment is optional until the workflow requires it. The recorded
  resolver remains separate from the current assignment.
- `(priority, sla_policy_version)` references the composite SLA policy key
  `(priority, policy_version)`.
- Resolution, cancellation, feedback and escalation state use ticket ID as their
  primary key, allowing at most one of each record per ticket.
- Escalation history is unique by `(ticket_id, escalation_level)`. Previous operational
  status is explicit; it is not inferred from the ticket's current `ESCALATED` label.
- History `changed_by` is a stable actor string, not a user foreign key. Services
  create the initial history entry atomically with the ticket.

## Authentication and audit

```mermaid
erDiagram
    app_user ||--o{ user_role : has
    app_role ||--o{ user_role : grants
    app_user o|--o| customer : optional_profile
    app_user o|--o| network_engineer : optional_profile
    app_user o|--o{ login_history : attempted_by
    app_user ||--o{ otp_challenge : verifies
    app_user o|--o{ audit_event : user_actor
    app_user ||--o{ ticket_cancellation : performs
    trouble_ticket ||--o{ audit_event : audited

    app_user {
        bigint user_id PK
        varchar username UK
        varchar password_hash
        bigint auth_version
        timestamptz locked_until
    }
    app_role {
        varchar role_code PK
    }
    user_role {
        bigint user_id PK, FK
        varchar role_code PK, FK
    }
    login_history {
        bigint history_id PK
        bigint user_id FK
        varchar outcome
    }
    otp_challenge {
        uuid challenge_id PK
        bigint user_id FK
        varchar purpose
        varchar code_hash
        timestamptz expires_at
    }
    audit_event {
        bigint audit_id PK
        bigint ticket_id FK
        bigint actor_user_id FK
        varchar actor_kind
        varchar action
    }
```

Customer and engineer `user_id` links are nullable and unique: profiles can exist
without a login. Login history may have no matched account. `USER` audit records
require `actor_user_id`; `SYSTEM` records require it to be null. Service-desk and
manager access comes from roles, not fabricated customer/engineer profiles.

## Network events and notifications

```mermaid
erDiagram
    network_node ||--o{ service_network_node : serves
    telecom_service ||--o{ service_network_node : mapped_to
    network_node ||--o{ network_event : emits
    app_user ||--o{ network_event : submits
    network_event ||--o{ event_ticket : creates
    telecom_service ||--o{ event_ticket : affected_service
    trouble_ticket ||--o| event_ticket : source_event
    trouble_ticket ||--o{ notification : generates
    customer o|--o{ notification : customer_recipient
    network_engineer o|--o{ notification : engineer_recipient
    escalation_recipient o|--o{ notification : level_recipient
    escalation_history o|--o{ notification : escalation_source
    notification ||--o| notification_receipt : delivered_as

    network_node {
        bigint node_id PK
        varchar node_code UK
        boolean active
    }
    service_network_node {
        bigint service_id PK, FK
        bigint node_id PK, FK
    }
    network_event {
        bigint event_id PK
        uuid event_key UK
        bigint node_id FK
        bigint created_by FK
        varchar event_type
        varchar processing_status
        integer attempts
    }
    event_ticket {
        bigint event_id PK, FK
        bigint service_id PK, FK
        bigint ticket_id FK, UK
    }
    notification {
        bigint notification_id PK
        bigint ticket_id FK
        bigint customer_id FK
        bigint engineer_id FK
        smallint escalation_recipient_level FK
        bigint escalation_id FK
        varchar delivery_status
        timestamptz read_at
        integer attempts
    }
    notification_receipt {
        bigint notification_id PK, FK
        varchar channel
        timestamptz delivered_at
    }
```

- Services and nodes are many-to-many through `service_network_node`.
- A UUID event key deduplicates simulator submissions. `(event_id, service_id)`
  prevents duplicate tickets for the same affected service/event. Unique ticket ID
  permits at most one source-event association per ticket.
- A composite foreign key also ensures the associated ticket belongs to the stored service.
- Each notification has **exactly one** customer, engineer or configured escalation-level
  recipient. The three optional diagram edges express alternatives, not three recipients
  on the same row.
- A receipt is unique per notification; `read_at` remains separate from delivery status.

`category_specialization` configures category-to-skill rules using controlled incident
categories. `schema_migration` is the technical version/checksum ledger. They are
omitted from the relationship views because they are not ticket ownership links.
Report jobs are in-memory, so there is no report-job table.

## Integrity and indexing

| Mechanism | Examples |
|---|---|
| Business uniqueness | Customer number, service code, employee code, ticket number, username and event key |
| Controlled values | PostgreSQL priority/status/service/category enums; CHECK catalogs for lifecycle/delivery/event values |
| Append-only guards | Status history, SLA policies, audit, resolution/cancellation/feedback, escalation history, event associations and delivery receipts |
| Access/list indexes | Customer services, engineer/status tickets, chronological ticket/history views and role links |
| Due-work indexes | Unanswered response deadlines, active resolution deadlines, pending notifications and pending events |
| Replay protection | Event/service uniqueness, one initial ticket-history row, one creation notification and escalation recipient uniqueness |
| Concurrency | `FOR UPDATE`, ordered engineer locks, `SKIP LOCKED` worker reads and optimistic `row_version` predicates |

Business transitions and history continuity are enforced by services/transactions;
the schema does not automatically execute the entire workflow or calculate deadlines.

## Migration inventory

| Version | Change |
|---|---|
| [V001](../../db/migrations/V001__initial_schema.sql) | Six core entities, enums, indexes and history/SLA guards |
| [V002](../../db/migrations/V002__authentication.sql) | Users, roles, login history, OTP challenges and profile links |
| [V003](../../db/migrations/V003__ticket_intake.sql) | Notification/audit foundation and ticket-number sequence |
| [V004](../../db/migrations/V004__engineer_assignment.sql) | Skill rules, engineer notification recipients and assignment audit |
| [V005](../../db/migrations/V005__manual_lifecycle.sql) | Resolution, cancellation and feedback metadata |
| [V006](../../db/migrations/V006__sla_escalation.sql) | Escalation state/history/recipients and SYSTEM audit actors |
| [V007](../../db/migrations/V007__notifications_network_events.sql) | Delivery receipts/retries, nodes, mappings, events and ticket associations |
| [V008](../../db/migrations/V008__case_study_resolution_codes.sql) | Additional case-study resolution codes with historical compatibility |

Applied migrations are immutable. The explicit runner validates SHA-256 checksums
and a contiguous ledger before applying pending versions transactionally. Setup
refuses a populated schema; application startup does not migrate. Future schema
changes begin at V009. See [migration policy](../../db/migrations/README.md).
