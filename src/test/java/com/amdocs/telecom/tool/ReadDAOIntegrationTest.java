package com.amdocs.telecom.tool;

import com.amdocs.telecom.config.TimeConfiguration;
import com.amdocs.telecom.dao.*;
import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.dto.PageRequest;
import com.amdocs.telecom.model.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Never loads application.properties or uses the normal development datasource. */
@EnabledIfSystemProperty(named = "telecom.dao.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReadDAOIntegrationTest {

    private static final PageRequest PAGE = new PageRequest(100, 0);
    private static final Instant CREATED = Instant.parse("2026-10-15T12:00:00.123456Z");
    private static final Instant UPDATED = CREATED.plusSeconds(1);

    private AnnotationConfigApplicationContext context;
    private CustomerDAO customers;
    private TelecomServiceDAO services;
    private NetworkEngineerDAO engineers;
    private TroubleTicketDAO tickets;
    private TicketStatusHistoryDAO histories;
    private SLAConfigurationDAO policies;
    private long customerId;
    private long serviceId;
    private long engineerId;
    private long firstTicketId;
    private long secondTicketId;

    @BeforeAll
    void installFreshFixturesAndWireRepositories() throws Exception {
        String url = System.getenv("TELECOM_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/telecom_oss_dao_test")) {
            throw new IllegalStateException("Run scripts/Test-DaoIntegration.ps1 with its disposable database");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(url,
                System.getenv("TELECOM_TEST_DB_USERNAME"), System.getenv("TELECOM_TEST_DB_PASSWORD"));
        Properties options = new Properties();
        options.setProperty("options", "-c TimeZone=Asia/Kolkata");
        source.setConnectionProperties(options);

        // install() refuses an occupied schema before touching existing objects/data.
        DatabaseSetup.install(source, true);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        addEdgeCaseFixtures(jdbc);
        options.setProperty("options", "-c TimeZone=Asia/Kolkata -c default_transaction_read_only=on");
        source.setConnectionProperties(options);

        context = new AnnotationConfigApplicationContext();
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.register(JdbcCustomerDAO.class, JdbcTelecomServiceDAO.class, JdbcNetworkEngineerDAO.class,
                JdbcTroubleTicketDAO.class, JdbcTicketStatusHistoryDAO.class, JdbcSLAConfigurationDAO.class,
                TimeConfiguration.class);
        context.refresh();
        customers = context.getBean(CustomerDAO.class);
        services = context.getBean(TelecomServiceDAO.class);
        engineers = context.getBean(NetworkEngineerDAO.class);
        tickets = context.getBean(TroubleTicketDAO.class);
        histories = context.getBean(TicketStatusHistoryDAO.class);
        policies = context.getBean(SLAConfigurationDAO.class);
        assertThat(jdbc.queryForObject("SHOW default_transaction_read_only", String.class)).isEqualTo("on");
    }

    private void addEdgeCaseFixtures(JdbcTemplate jdbc) {
        customerId = jdbc.queryForObject("INSERT INTO customer (customer_number, customer_name, email, "
                        + "mobile_number, customer_type, city, status, created_at, updated_at) "
                        + "VALUES ('CUST-TEST', ?, 'reader@example.test', '+910000000099', 'SME', 'Pune', 'INACTIVE', ?, ?) "
                        + "RETURNING customer_id", Long.class,
                "O'Brien Telecom", Timestamp.from(CREATED), Timestamp.from(UPDATED));
        serviceId = jdbc.queryForObject("INSERT INTO telecom_service (customer_id, service_code, service_name, "
                        + "service_type, region, activation_date, service_status, created_at, updated_at) "
                        + "VALUES (?, 'SVC-TEST', 'Test VPN', 'VPN', 'West', DATE '2024-02-29', 'SUSPENDED', ?, ?) "
                        + "RETURNING service_id", Long.class,
                customerId, Timestamp.from(CREATED), Timestamp.from(UPDATED));
        engineerId = jdbc.queryForObject("INSERT INTO network_engineer (employee_code, engineer_name, "
                        + "specialization, region, experience_years, available, created_at, updated_at) "
                        + "VALUES ('ENG-TEST', 'Test Engineer', 'IP Network', 'West', 12, TRUE, ?, ?) RETURNING engineer_id",
                Long.class, Timestamp.from(CREATED), Timestamp.from(UPDATED));
        firstTicketId = jdbc.queryForObject("INSERT INTO trouble_ticket (ticket_number, service_id, "
                        + "assigned_engineer_id, category, description, priority, severity, sla_policy_version, status, "
                        + "response_deadline, resolution_deadline, first_response_at, row_version, created_at, updated_at) "
                        + "VALUES ('TT-TEST-A', ?, ?, 'ROAMING', ?, 'CRITICAL', 'HIGH', 1, 'IN_PROGRESS', ?, ?, ?, 7, ?, ?) "
                        + "RETURNING ticket_id", Long.class,
                serviceId, engineerId, "Line one\nO'Brien's roaming problem", Timestamp.from(CREATED.plusSeconds(900)),
                Timestamp.from(CREATED.plusSeconds(7200)), Timestamp.from(CREATED),
                Timestamp.from(CREATED), Timestamp.from(UPDATED));
        secondTicketId = jdbc.queryForObject("INSERT INTO trouble_ticket (ticket_number, service_id, category, "
                        + "description, priority, severity, sla_policy_version, response_deadline, resolution_deadline, created_at) "
                        + "VALUES ('TT-TEST-B', ?, 'OTHER', 'Same creation time', 'CRITICAL', 'LOW', 1, ?, ?, ?) RETURNING ticket_id",
                Long.class, serviceId, Timestamp.from(CREATED.plusSeconds(900)), Timestamp.from(CREATED.plusSeconds(7200)),
                Timestamp.from(CREATED));
        jdbc.update("INSERT INTO ticket_status_history (ticket_id, old_status, new_status, changed_by, changed_at, remarks) "
                        + "VALUES (?, NULL, 'OPEN', 'TEST', ?, NULL), (?, 'OPEN', 'ASSIGNED', 'TEST', ?, 'Assigned'), "
                        + "(?, 'ASSIGNED', 'IN_PROGRESS', 'TEST', ?, 'Acknowledged')",
                firstTicketId, Timestamp.from(CREATED), firstTicketId, Timestamp.from(CREATED),
                firstTicketId, Timestamp.from(CREATED));
        jdbc.update("INSERT INTO ticket_status_history (ticket_id, new_status, changed_by, changed_at) "
                        + "VALUES (?, 'OPEN', 'TEST', ?)", secondTicketId, Timestamp.from(CREATED));
        jdbc.update("INSERT INTO sla_configuration (priority, policy_version, response_minutes, resolution_minutes, "
                        + "warning_percent, created_at) VALUES ('CRITICAL', 2, 10, 100, 75, ?)", Timestamp.from(UPDATED));
    }

    @AfterAll
    void closeContext() {
        if (context != null) { context.close(); }
    }

    @Test
    void mapsCustomerFieldsIncludingQuotedNameAndTimestamps() {
        Customer customer = customers.findByCustomerNumber("CUST-TEST").get();
        assertThat(customer.getCustomerId()).isEqualTo(customerId);
        assertThat(customer.getCustomerNumber()).isEqualTo("CUST-TEST");
        assertThat(customer.getCustomerName()).isEqualTo("O'Brien Telecom");
        assertThat(customer.getEmail()).isEqualTo("reader@example.test");
        assertThat(customer.getMobileNumber()).isEqualTo("+910000000099");
        assertThat(customer.getCustomerType()).isEqualTo(CustomerType.SME);
        assertThat(customer.getCity()).isEqualTo("Pune");
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(customer.getCreatedAt()).isEqualTo(CREATED);
        assertThat(customer.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(customers.findById(customerId).get().getCustomerNumber()).isEqualTo("CUST-TEST");
    }

    @Test
    void mapsServiceFieldsAndLeapDayWithoutTimezoneDrift() {
        TelecomService service = services.findByServiceCode("SVC-TEST").get();
        assertThat(service.getServiceId()).isEqualTo(serviceId);
        assertThat(service.getCustomerId()).isEqualTo(customerId);
        assertThat(service.getServiceCode()).isEqualTo("SVC-TEST");
        assertThat(service.getServiceName()).isEqualTo("Test VPN");
        assertThat(service.getServiceType()).isEqualTo(ServiceType.VPN);
        assertThat(service.getRegion()).isEqualTo("West");
        assertThat(service.getActivationDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(service.getServiceStatus()).isEqualTo(ServiceStatus.SUSPENDED);
        assertThat(service.getCreatedAt()).isEqualTo(CREATED);
        assertThat(service.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(services.findById(serviceId).get().getServiceCode()).isEqualTo("SVC-TEST");
    }

    @Test
    void mapsEngineerFieldsAndAvailability() {
        NetworkEngineer engineer = engineers.findByEmployeeCode("ENG-TEST").get();
        assertThat(engineer.getEngineerId()).isEqualTo(engineerId);
        assertThat(engineer.getEmployeeCode()).isEqualTo("ENG-TEST");
        assertThat(engineer.getEngineerName()).isEqualTo("Test Engineer");
        assertThat(engineer.getSpecialization()).isEqualTo("IP Network");
        assertThat(engineer.getRegion()).isEqualTo("West");
        assertThat(engineer.getExperienceYears()).isEqualTo(12);
        assertThat(engineer.isAvailable()).isTrue();
        assertThat(engineer.getCreatedAt()).isEqualTo(CREATED);
        assertThat(engineer.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(engineers.findById(engineerId).get().getEmployeeCode()).isEqualTo("ENG-TEST");
        assertThat(engineers.findByEmployeeCode("ENG1015").get().isAvailable()).isFalse();
    }

    @Test
    void unassignedAndUnfinishedTicketFieldsRemainNull() {
        TroubleTicket ticket = tickets.findByTicketNumber("TT-2026-000001").get();
        assertThat(ticket.getAssignedEngineerId()).isNull();
        assertThat(ticket.getFirstResponseAt()).isNull();
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getCategory()).isEqualTo(IncidentCategory.NETWORK_OUTAGE);
    }

    @Test
    void mapsCompleteTicketSnapshotWithSeparatePriorityAndSeverity() {
        TroubleTicket ticket = tickets.findById(firstTicketId).get();
        assertThat(ticket.getTicketId()).isEqualTo(firstTicketId);
        assertThat(ticket.getTicketNumber()).isEqualTo("TT-TEST-A");
        assertThat(ticket.getServiceId()).isEqualTo(serviceId);
        assertThat(ticket.getAssignedEngineerId()).isEqualTo(engineerId);
        assertThat(ticket.getCategory()).isEqualTo(IncidentCategory.ROAMING);
        assertThat(ticket.getDescription()).isEqualTo("Line one\nO'Brien's roaming problem");
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(ticket.getSlaPolicyVersion()).isEqualTo(1);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getResponseDeadline()).isEqualTo(CREATED.plusSeconds(900));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(CREATED.plusSeconds(7200));
        assertThat(ticket.getFirstResponseAt()).isEqualTo(CREATED);
        assertThat(ticket.getRowVersion()).isEqualTo(7L);
        assertThat(ticket.getCreatedAt()).isEqualTo(CREATED);
        assertThat(ticket.getUpdatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void mapsCompletedResolutionAndClosureInstants() {
        TroubleTicket ticket = tickets.findByTicketNumber("TT-2026-000007").get();
        Instant reference = Instant.parse("2026-10-15T12:00:00Z");
        assertThat(ticket.getCreatedAt()).isEqualTo(reference.minusSeconds(24 * 3600));
        assertThat(ticket.getFirstResponseAt()).isEqualTo(reference.minusSeconds(23 * 3600));
        assertThat(ticket.getResolvedAt()).isEqualTo(reference.minusSeconds(4 * 3600));
        assertThat(ticket.getClosedAt()).isEqualTo(reference.minusSeconds(2 * 3600));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void historyPreservesNullableValuesAndUsesIdToBreakTimestampTies() {
        List<TicketStatusHistory> history = histories.findByTicketId(firstTicketId, PAGE);
        assertThat(history).extracting(TicketStatusHistory::getNewStatus)
                .containsExactly(TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
        assertThat(history).extracting(TicketStatusHistory::getOldStatus)
                .containsExactly(null, TicketStatus.OPEN, TicketStatus.ASSIGNED);
        assertThat(history).extracting(TicketStatusHistory::getHistoryId).isSorted();
        assertThat(history).allSatisfy(entry -> {
            assertThat(entry.getTicketId()).isEqualTo(firstTicketId);
            assertThat(entry.getChangedAt()).isEqualTo(CREATED);
            assertThat(entry.getChangedBy()).isEqualTo("TEST");
        });
        assertThat(history.get(0).getRemarks()).isNull();
        assertThat(history.get(2).getRemarks()).isEqualTo("Acknowledged");
        assertThat(histories.findById(history.get(2).getHistoryId()).get().getNewStatus())
                .isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(histories.findByTicketId(firstTicketId, new PageRequest(1, 1)).get(0).getNewStatus())
                .isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void latestPolicyLookupDoesNotReplaceHistoricalTicketPolicy() {
        SLAConfiguration latest = policies.findLatestByPriority(TicketPriority.CRITICAL).get();
        assertThat(latest.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(latest.getPolicyVersion()).isEqualTo(2);
        assertThat(latest.getResponseMinutes()).isEqualTo(10);
        assertThat(latest.getResolutionMinutes()).isEqualTo(100);
        assertThat(latest.getWarningPercent()).isEqualTo(75);
        assertThat(latest.getCreatedAt()).isEqualTo(UPDATED);
        SLAConfiguration original = policies.findByPriorityAndVersion(TicketPriority.CRITICAL, 1).get();
        assertThat(original.getResponseMinutes()).isEqualTo(15);
        assertThat(original.getResolutionMinutes()).isEqualTo(120);
        assertThat(original.getWarningPercent()).isEqualTo(80);
        assertThat(original.getCreatedAt()).isNotNull();
        assertThat(tickets.findById(firstTicketId).get().getSlaPolicyVersion()).isEqualTo(1);
    }

    @Test
    void nonexistentRecordsReturnEmptyResults() {
        assertThat(customers.findById(-1)).isEmpty();
        assertThat(customers.findByCustomerNumber("absent")).isEmpty();
        assertThat(services.findById(-1)).isEmpty();
        assertThat(services.findByServiceCode("absent")).isEmpty();
        assertThat(engineers.findById(-1)).isEmpty();
        assertThat(engineers.findByEmployeeCode("absent")).isEmpty();
        assertThat(tickets.findById(-1)).isEmpty();
        assertThat(tickets.findByTicketNumber("absent")).isEmpty();
        assertThat(histories.findById(-1)).isEmpty();
        assertThat(policies.findByPriorityAndVersion(TicketPriority.CRITICAL, 999)).isEmpty();
        assertThat(services.findByCustomerId(-1, PAGE)).isEmpty();
        assertThat(tickets.findByCustomerId(-1, PAGE)).isEmpty();
        assertThat(tickets.findByAssignedEngineerId(-1, PAGE)).isEmpty();
        assertThat(histories.findByTicketId(-1, PAGE)).isEmpty();
    }

    @Test
    void queryParametersAreLiteralValuesRatherThanSql() {
        String attack = "' OR '1'='1";
        assertThat(customers.findByCustomerNumber(attack)).isEmpty();
        assertThat(services.findByServiceCode(attack)).isEmpty();
        assertThat(engineers.findByEmployeeCode(attack)).isEmpty();
        assertThat(tickets.findByTicketNumber(attack)).isEmpty();
        assertThat(engineers.findAvailableBySpecializationAndRegion(attack, "West", PAGE)).isEmpty();
    }

    @Test
    void listQueriesRespectOwnershipEngineerEligibilityAndEnumFilters() {
        assertThat(services.findByCustomerId(customerId, PAGE)).extracting(TelecomService::getServiceId)
                .containsExactly(serviceId);
        assertThat(tickets.findByCustomerId(customerId, PAGE)).extracting(TroubleTicket::getTicketId)
                .containsExactly(secondTicketId, firstTicketId);
        assertThat(tickets.findByAssignedEngineerId(engineerId, PAGE)).extracting(TroubleTicket::getTicketId)
                .containsExactly(firstTicketId);
        assertThat(engineers.findAvailableBySpecializationAndRegion("IP Network", "West", PAGE))
                .extracting(NetworkEngineer::getEngineerId).containsExactly(engineerId);
        assertThat(engineers.findAvailableBySpecializationAndRegion("RAN", "North", PAGE)).isEmpty();
        for (TicketStatus status : TicketStatus.values()) {
            assertThat(tickets.findByStatus(status, PAGE)).isNotEmpty()
                    .allSatisfy(ticket -> assertThat(ticket.getStatus()).isEqualTo(status));
        }
        for (TicketPriority priority : TicketPriority.values()) {
            assertThat(tickets.findByPriority(priority, PAGE)).isNotEmpty()
                    .allSatisfy(ticket -> assertThat(ticket.getPriority()).isEqualTo(priority));
        }
    }

    @Test
    void ticketPagingIsBoundedAndStableForEqualCreationTimes() {
        assertThat(tickets.findByCustomerId(customerId, new PageRequest(1, 0)))
                .extracting(TroubleTicket::getTicketId).containsExactly(secondTicketId);
        assertThat(tickets.findByCustomerId(customerId, new PageRequest(1, 1)))
                .extracting(TroubleTicket::getTicketId).containsExactly(firstTicketId);
        assertThat(tickets.findByCustomerId(customerId, new PageRequest(1, 2))).isEmpty();
        assertThat(services.findByCustomerId(customerId, new PageRequest(1, 1))).isEmpty();
        assertThat(histories.findByTicketId(firstTicketId, new PageRequest(1, 3))).isEmpty();
    }

    @Test
    void javaEnumsMatchNativePostgreSqlControlledValues() {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForList("SELECT unnest(enum_range(NULL::ticket_status))::text", String.class))
                .containsExactlyElementsOf(names(TicketStatus.values()));
        assertThat(jdbc.queryForList("SELECT unnest(enum_range(NULL::ticket_priority))::text", String.class))
                .containsExactlyElementsOf(names(TicketPriority.values()));
        assertThat(jdbc.queryForList("SELECT unnest(enum_range(NULL::service_type))::text", String.class))
                .containsExactlyElementsOf(names(ServiceType.values()));
        assertThat(jdbc.queryForList("SELECT unnest(enum_range(NULL::incident_category))::text", String.class))
                .containsExactlyElementsOf(names(IncidentCategory.values()));
        assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
    }

    private List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toList());
    }
}
