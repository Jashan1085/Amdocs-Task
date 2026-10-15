# Class diagrams

[Design index](README.md) · [Database ER diagrams](DATABASE.md) · [Sequences](SEQUENCES.md)

## Contents

1. [Core domain classes](#core-domain-classes)
2. [Application services](#application-services)
3. [Formatting and delivery interfaces](#formatting-and-delivery-interfaces)
4. [Reading the diagrams](#reading-the-diagrams)

## Core domain classes

These six classes are immutable Java persistence snapshots. The diagram selects
important fields; constructors/getters and other profile fields are omitted.
Associations describe persisted relationships through IDs, not object-reference
navigation or ORM mappings.

```mermaid
classDiagram
    class Customer {
        -long customerId
        -String customerNumber
        -String customerName
        -CustomerType customerType
        -CustomerStatus status
    }
    class TelecomService {
        -long serviceId
        -long customerId
        -String serviceCode
        -ServiceType serviceType
        -String region
        -ServiceStatus serviceStatus
    }
    class NetworkEngineer {
        -long engineerId
        -String employeeCode
        -String specialization
        -String region
        -int experienceYears
        -boolean available
    }
    class TroubleTicket {
        -long ticketId
        -String ticketNumber
        -long serviceId
        -Long assignedEngineerId
        -IncidentCategory category
        -TicketPriority priority
        -Severity severity
        -int slaPolicyVersion
        -TicketStatus status
        -Instant responseDeadline
        -Instant resolutionDeadline
        -Instant firstResponseAt
        -Instant resolvedAt
        -long rowVersion
    }
    class TicketStatusHistory {
        -long historyId
        -long ticketId
        -TicketStatus oldStatus
        -TicketStatus newStatus
        -String changedBy
        -Instant changedAt
        -String remarks
    }
    class SLAConfiguration {
        -TicketPriority priority
        -int policyVersion
        -int responseMinutes
        -int resolutionMinutes
        -int warningPercent
    }
    Customer "1" --> "0..*" TelecomService : owns
    TelecomService "1" --> "0..*" TroubleTicket : receives
    NetworkEngineer "0..1" --> "0..*" TroubleTicket : current assignee
    SLAConfiguration "1" --> "0..*" TroubleTicket : priority and version
    TroubleTicket "1" --> "0..*" TicketStatusHistory : persisted history
```

Workload is derived from active assigned tickets, not stored as a mutable field on
`NetworkEngineer`. Customer ownership is derived through `TelecomService`.

## Application services

The classes and method names below exist in the source. Parameters and less relevant
dependencies are omitted to keep the diagram readable. Arrows represent dependencies.

```mermaid
classDiagram
    class TicketController
    class AssignmentController
    class LifecycleController
    class SlaController
    class ReportController
    class TicketWorkflowService {
        +create()
        +ticket()
        +history()
    }
    class AssignmentService {
        +options()
        +assign()
    }
    class LifecycleService {
        +read()
        +act()
        +feedback()
    }
    class SLAService {
        +targets()
        +evaluate()
    }
    class SlaOperationsService {
        +priority()
        +resume()
        +counters()
    }
    class EscalationService {
        +inspect()
    }
    class ReportService {
        +request()
        +status()
        +download()
        +delete()
    }
    class JdbcTicketWorkflowDAO
    class JdbcAssignmentDAO
    class JdbcLifecycleDAO
    class JdbcSlaDAO
    class JdbcReportDAO
    TicketController --> TicketWorkflowService
    TicketController --> LifecycleService
    AssignmentController --> AssignmentService
    LifecycleController --> LifecycleService
    SlaController --> SlaOperationsService
    ReportController --> ReportService
    TicketWorkflowService --> SLAService
    TicketWorkflowService --> JdbcTicketWorkflowDAO
    AssignmentService --> TicketWorkflowService
    AssignmentService --> JdbcAssignmentDAO
    LifecycleService --> TicketWorkflowService
    LifecycleService --> JdbcLifecycleDAO
    SlaOperationsService --> SLAService
    SlaOperationsService --> JdbcSlaDAO
    EscalationService --> SLAService
    EscalationService --> JdbcSlaDAO
    ReportService --> TicketWorkflowService
    ReportService --> JdbcReportDAO
```

`TicketWorkflowService` also centralizes current-account/profile authorization used
by related services. `NetworkEventService` invokes its internal system-ticket creation
method within the event transaction; it does not impersonate a desk session.

## Formatting and delivery interfaces

```mermaid
classDiagram
    class OtpDelivery {
        <<interface>>
        +deliver()
        +preview()
        +forget()
        +isDemo()
    }
    class DevOtpDelivery
    class AuthenticationService
    class NotificationDelivery {
        <<interface>>
        +deliver()
    }
    class InAppNotificationDelivery
    class NotificationService
    class ReportFormatter {
        <<interface>>
        +format()
    }
    class ReportFormatterFactory {
        +create() ReportFormatter
    }
    class ReportEncoder {
        +encode()
    }
    OtpDelivery <|.. DevOtpDelivery
    AuthenticationService --> OtpDelivery
    NotificationDelivery <|.. InAppNotificationDelivery
    NotificationService --> NotificationDelivery
    ReportFormatterFactory ..> ReportFormatter : creates CSV or TXT strategy
    ReportEncoder --> ReportFormatterFactory
    ReportEncoder ..> ReportFormatter : delegates formatting
```

The non-development OTP fallback is a configured lambda that reports delivery
unavailable. CSV/TXT strategies are produced as implementations of the functional
`ReportFormatter` interface; no separate named CSV/TXT implementation classes are
invented here.

## Reading the diagrams

- Domain enums control priorities, statuses, service types, categories and resolution codes.
- Database entities such as notification/event/audit records are not all separate
  Java model classes: several JDBC APIs use bounded map projections. Their tables
  appear in the [ER diagrams](DATABASE.md), not as fictional Java classes.
- Read DAO interfaces and row mappers are described in [DATA_ACCESS.md](../DATA_ACCESS.md).
- Transaction boundaries and worker coordination appear in the [sequence diagrams](SEQUENCES.md).
- Concrete pattern/Java requirement evidence is indexed in [ASSESSMENT.md](../ASSESSMENT.md).
