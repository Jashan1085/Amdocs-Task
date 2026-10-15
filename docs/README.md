# TelecomOSS documentation

[Project README](../README.md)

## Contents

1. [Choose a reading path](#choose-a-reading-path)
2. [Documentation tree](#documentation-tree)
3. [Design and workflows](#design-and-workflows)
4. [Feature reference](#feature-reference)
5. [Development and verification](#development-and-verification)

## Choose a reading path

| You want to… | Start here | Continue with |
|---|---|---|
| Understand the project | [Project overview](../README.md#overview) | [Role workflows](WORKFLOWS.md) |
| Install and demonstrate it | [Setup/demo](DEMO.md) | [Authentication](AUTHENTICATION.md), [role workflows](WORKFLOWS.md) |
| Review system design | [Design index](design/README.md) | [Classes](design/CLASS_DIAGRAMS.md), [ER diagrams](design/DATABASE.md), [sequences](design/SEQUENCES.md) |
| Understand business rules | [Workflows](WORKFLOWS.md) | Relevant feature guide below |
| Review Java/JDBC requirements | [Assessment map](ASSESSMENT.md) | [Tests](../README.md#testing-and-verification), [synthetic samples](samples/) |
| Continue development | [Handoff tracker](../MILESTONES.md) | [Agent/repository rules](../AGENTS.md), [architecture](ARCHITECTURE.md) |

## Documentation tree

```text
README.md                         Project landing page and quick start
docs/
├── README.md                     This documentation index
├── WORKFLOWS.md                  Roles, permissions and business journeys
├── design/
│   ├── README.md                 Context, layers and local deployment
│   ├── CLASS_DIAGRAMS.md          Domain and application class relationships
│   ├── DATABASE.md               ER diagrams and migration inventory
│   └── SEQUENCES.md              State machine and processing sequences
├── DEMO.md                       Installation and complete demonstration
├── AUTHENTICATION.md             Login, CAPTCHA, OTP and password reset
├── TICKET_WORKFLOW.md            Customer/desk intake, tracking and ownership
├── ASSIGNMENT.md                 Recommendation and reassignment rules
├── LIFECYCLE.md                  Acknowledgment, resolution, closure and feedback
├── SLA_MONITORING.md             Timers, escalation and priority changes
├── NOTIFICATIONS_EVENTS.md       Inboxes, retries, nodes and simulated events
├── REPORTS.md                    Dashboard/report semantics and job limits
├── DATA_ACCESS.md                JDBC interfaces, mapping and paging
├── ASSESSMENT.md                 Requirement-to-code/test evidence
├── ARCHITECTURE.md               Operational invariants and scope limits
└── samples/                      Synthetic CSV and TXT examples
db/
├── README.md                     Database setup and seed reference
└── migrations/README.md          Migration inventory and checksum policy
MILESTONES.md                     Dated implementation and verification history
BUILD_PLAN.md                     Original implementation sequence
AGENTS.md                         Maintenance rules for future contributors/agents
```

## Design and workflows

The design diagrams use GitHub Mermaid code blocks. They document the current
implementation, with selected fields/dependencies for readability; SQL migrations
and Java source remain authoritative.

- [System context, layered architecture and deployment](design/README.md)
- [Domain model, service classes and formatting strategies](design/CLASS_DIAGRAMS.md)
- [Core, security, notification and event ER diagrams](design/DATABASE.md)
- [Ticket state machine and authentication/ticket/event/report sequences](design/SEQUENCES.md)
- [Role journeys and permission matrix](WORKFLOWS.md)

## Feature reference

| Topic | Guide | Main implementation entrypoints |
|---|---|---|
| Login and recovery | [Authentication](AUTHENTICATION.md) | `AuthenticationController`, `AuthenticationService`, `CaptchaService` |
| Services and ticket intake | [Ticket workflow](TICKET_WORKFLOW.md) | `TicketController`, `TicketWorkflowService` |
| Engineer recommendations | [Assignment](ASSIGNMENT.md) | `AssignmentService`, `EngineerRanking` |
| Ticket actions and feedback | [Lifecycle](LIFECYCLE.md) | `LifecycleService`, `TicketTransitions` |
| SLA and escalation | [SLA monitoring](SLA_MONITORING.md) | `SLAService`, `EscalationService`, `SlaOperationsService` |
| Inboxes and network events | [Notifications/events](NOTIFICATIONS_EVENTS.md) | `NotificationService`, `NetworkEventService`, `EventDispatcher`, `BackgroundJobs` |
| Dashboards and exports | [Reports](REPORTS.md) | `DashboardService`, `ReportService`, `JdbcReportDAO` |
| Persistence | [Data access](DATA_ACCESS.md) | Read DAO interfaces, JDBC implementations and row mappers |

## Development and verification

- [Setup and multi-role demonstration](DEMO.md)
- [Test commands and last recorded results](../README.md#testing-and-verification)
- [Database setup](../db/README.md) and [immutable migrations](../db/migrations/README.md)
- [Assessment evidence](ASSESSMENT.md), [CSV sample](samples/synthetic-incidents.csv) and [TXT sample](samples/synthetic-incidents.txt)
- [Architecture decisions and limitations](ARCHITECTURE.md)
- [Milestones and current handoff](../MILESTONES.md#current-handoff)

Shared docs contain no demo passwords or private datasource values. Provision your
own local credentials using the setup guide.
