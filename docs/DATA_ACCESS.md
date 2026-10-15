# Milestone 2: read data access

## Contracts

Six immutable Java 8 models live in `com.amdocs.telecom.model`, with eight enums
matching V001's native enum/check constraints. Severity and priority are different
types even though their initial values match. Models are persistence snapshots,
not forms; later services own lifecycle validation and later DTOs own form binding.

- IDs and required numeric values use primitives. Unassigned engineer IDs use `Long`
  and remain null; they never become zero. Unfinished lifecycle timestamps and the
  initial history's old status remain null.
- PostgreSQL TIMESTAMPTZ maps to `Instant`; activation DATE maps to `LocalDate`.
  `TimeConfiguration` supplies an injectable UTC `Clock`, replaceable in tests.
- Getters expose mapped values. Models do not cache workload, customer ownership on
  tickets, or calculated SLA state, and do not introduce setters or persistence writes.

Each interface in `com.amdocs.telecom.dao` has an `@Repository` JdbcTemplate
implementation in `dao.impl` and a dedicated reusable `RowMapper` in `dao.mapper`.
Constructor injection uses the application's existing JdbcTemplate bean.

| Interface | Read operations |
|---|---|
| CustomerDAO | findById, findByCustomerNumber |
| TelecomServiceDAO | findById, findByServiceCode, paged findByCustomerId |
| NetworkEngineerDAO | findById, findByEmployeeCode, paged findAvailableBySpecializationAndRegion |
| TroubleTicketDAO | findById, findByTicketNumber, paged findByCustomerId / findByAssignedEngineerId / findByStatus / findByPriority |
| TicketStatusHistoryDAO | findById, paged findByTicketId |
| SLAConfigurationDAO | findByPriorityAndVersion, findLatestByPriority |

Single-record methods return Optional.empty() for missing rows. Lists return empty
lists for no matches. DataAccessException is propagated for real database failures;
it is not converted into a missing record. Future controllers must handle exceptions
safely rather than exposing database diagnostics. There is no new HTTP endpoint.

All caller values, including LIMIT/OFFSET, are bound parameters. Native enum filter
values use explicit PostgreSQL casts. Specialization/region matching is exact and
case-sensitive. Eligibility queries are lookup support, not the later recommendation
ranking or assignment workflow.

Every list requires `new PageRequest(limit, offset)`: limit 1-100, nonnegative
zero-based offset. Orders are deterministic:

- Services: service ID ascending.
- Available engineers: employee code ascending.
- Tickets: creation time descending, ticket ID descending.
- History: change time ascending, history ID ascending.

Latest SLA means highest policy version **within the requested priority**, not latest
creation timestamp or highest version across all priorities. Existing tickets continue
to reference their own priority/version. Customer ticket queries join via the service.
DAOs do not authorize callers: service-layer ownership/role checks arrive in subsequent
milestones. Avoid exposing these methods directly as unsecured web endpoints.

## Verification

Ordinary tests need no database:

```powershell
.\scripts\mvn.ps1 test
```

The DAO integration harness requires installed PostgreSQL tools on PATH (initdb,
pg_ctl, createdb), Maven, and a Java 8 JDK. It starts its own loopback-only cluster
on an available port, creates `telecom_oss_dao_test`, runs unit and DAO tests, then
stops and removes the generated cluster. Temporary test authentication uses trust
only on that loopback instance; no application credentials are loaded.

```powershell
.\scripts\Test-DaoIntegration.ps1
```

An optional `-Port` chooses another available port. The tests require the harness's
explicit `TELECOM_TEST_DB_*` settings, refuse an occupied schema during fixture setup,
and switch to read-only connections after loading fixtures. They never load the
normal application.properties or private application-local.properties file.

Coverage includes all six mappings, null IDs/timestamps/history, subsecond instants
with a non-UTC PostgreSQL session, leap-day dates, native enum filters, ownership
joins, stable paging with timestamp ties, missing records, SQL-like string parameters,
Spring wiring and immutable SLA versions. Application development data stays intact.

The existing `-Dtelecom.integration=true` switch is separate: it enables the earlier
read-only baseline checks against your configured development database. It is not
needed by the temporary DAO harness and should not be added to that command.
