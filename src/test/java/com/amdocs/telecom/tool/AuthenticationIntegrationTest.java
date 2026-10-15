package com.amdocs.telecom.tool;

import com.amdocs.telecom.dao.impl.JdbcAuthenticationDAO;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.dto.LifecycleForm;
import com.amdocs.telecom.dto.FeedbackForm;
import com.amdocs.telecom.service.AuthenticationService;
import com.amdocs.telecom.support.MutableClock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full security/filter/controller/template/JDBC tests against an independent temporary database. */
@EnabledIfSystemProperty(named = "telecom.auth.integration", matches = "true")
@SpringBootTest(properties = {"spring.config.import=", "spring.sql.init.mode=never", "logging.level.root=OFF", "telecom.sla.monitor.enabled=false", "telecom.jobs.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AuthenticationIntegrationTest.TestClockConfiguration.class)
class AuthenticationIntegrationTest {
    private static final String PASSWORD = "Initial-Test-Password123!";
    private static final String NEW_PASSWORD = "Changed-Test-Password456!";
    private static final String BINDING = "a-long-test-session-binding-";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TELECOM_AUTH_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/telecom_oss_auth_test")) {
            throw new IllegalStateException("Run scripts/Test-AuthIntegration.ps1 with its disposable database");
        }
        try {
            DriverManagerDataSource source = new DriverManagerDataSource(url, "telecom_test", "");
            DatabaseSetup.install(source, true);
            DatabaseMigrate.upgrade(source);
        } catch (Exception exception) { throw new IllegalStateException("Temporary authentication schema setup failed", exception); }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "telecom_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthenticationService authentication;
    @Autowired private PasswordEncoder encoder;
    @Autowired private OtpDelivery delivery;
    @Autowired private MutableClock clock;
    @Autowired private javax.sql.DataSource source;
    @SpyBean private JdbcAuthenticationDAO accounts;
    @SpyBean private DevOtpDelivery devDelivery;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcTicketWorkflowDAO ticketWrites;
    @Autowired private com.amdocs.telecom.service.TicketWorkflowService workflow;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcAssignmentDAO assignmentWrites;
    @Autowired private com.amdocs.telecom.service.AssignmentService assignments;
    @Autowired private com.amdocs.telecom.service.LifecycleService lifecycle;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcLifecycleDAO lifecycleWrites;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcSlaDAO slaWrites;
    @Autowired private com.amdocs.telecom.service.EscalationService escalations;
    @Autowired private com.amdocs.telecom.service.SlaOperationsService slaOperations;
    @Autowired private com.amdocs.telecom.service.SLAService slaService;
    @Autowired private com.amdocs.telecom.service.SlaMonitor slaMonitor;
    @Autowired private com.amdocs.telecom.service.NotificationService notifications;
    @SpyBean private com.amdocs.telecom.service.InAppNotificationDelivery notificationDelivery;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcNotificationDAO notificationWrites;
    @Autowired private com.amdocs.telecom.service.NetworkEventService networkEvents;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcNetworkEventDAO eventWrites;
    @Autowired private com.amdocs.telecom.service.DashboardService dashboards;
    @Autowired private com.amdocs.telecom.service.ReportService reports;
    @SpyBean private com.amdocs.telecom.dao.impl.JdbcReportDAO reportQueries;
    private AuthAccount customer;

    @BeforeEach void fixture() {
        clock.reset();
        customer = createAccount(Role.CUSTOMER);
    }

    private long serviceFor(AuthAccount owner) {
        return jdbc.queryForObject("INSERT INTO telecom_service (customer_id,service_code,service_name,service_type,region,activation_date) "
                + "VALUES (?,?,'Test Broadband','BROADBAND','West',DATE '2026-01-01') RETURNING service_id", Long.class,
                owner.getCustomerId(), "TEST-"+UUID.randomUUID().toString().replace("-", ""));
    }
    private com.amdocs.telecom.dto.TicketCreateForm ticketForm(long serviceId) {
        com.amdocs.telecom.dto.TicketCreateForm form=new com.amdocs.telecom.dto.TicketCreateForm();
        form.setServiceId(serviceId); form.setCategory(com.amdocs.telecom.model.IncidentCategory.NO_CONNECTIVITY);
        form.setPriority(com.amdocs.telecom.model.TicketPriority.LOW); form.setSeverity(com.amdocs.telecom.model.Severity.HIGH);
        form.setDescription("  Link is down <script>alert('test')</script>  "); return form;
    }

    @Test void customerCreatesPersistentTicketWithDeadlinesHistoryNotificationAndAudit() throws Exception {
        long serviceId=serviceFor(customer);
        MockHttpSession session=fullLogin(customer);
        mvc.perform(get("/customer/services").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Raise ticket")));
        mvc.perform(get("/customer/tickets/new").param("serviceId",String.valueOf(serviceId)).session(session))
                .andExpect(status().isOk()).andExpect(view().name("tickets/new"));
        MvcResult created=mvc.perform(post("/customer/tickets").session(session).with(csrf())
                .param("serviceId",String.valueOf(serviceId)).param("category","NO_CONNECTIVITY")
                .param("priority","LOW").param("severity","HIGH").param("description",ticketForm(serviceId).getDescription())
                .param("customerId","999999").param("changedBy","forged-actor"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String url=created.getResponse().getRedirectedUrl();
        long id=Long.parseLong(url.substring(url.lastIndexOf('/')+1));
        com.amdocs.telecom.model.TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getStatus()).isEqualTo(com.amdocs.telecom.model.TicketStatus.OPEN);
        assertThat(ticket.getTicketNumber()).matches("TT-[0-9]{10,}");
        assertThat(ticket.getAssignedEngineerId()).isNull();
        assertThat(ticket.getResponseDeadline()).isEqualTo(clock.instant().plusSeconds(480*60));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(clock.instant().plusSeconds(2880*60));
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=? AND old_status IS NULL AND new_status='OPEN' AND changed_by=?",id,"user:"+customer.getUserId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND customer_id=? AND event_type='TICKET_CREATED' AND delivery_status='PENDING' AND read_at IS NULL",id,customer.getCustomerId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND actor_user_id=?",id,customer.getUserId())).isEqualTo(1);
        mvc.perform(get(url).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Status history")));
        mvc.perform(get("/customer/tickets").session(session).param("status","OPEN").param("priority","LOW"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(ticket.getTicketNumber())));
        // A fresh DAO instance reads the committed row; no in-memory ticket store is involved.
        assertThat(new com.amdocs.telecom.dao.impl.JdbcTroubleTicketDAO(jdbc).findById(id)).isPresent();
    }

    @Test void customersCannotReadOrCreateTicketsAgainstAnotherCustomer() throws Exception {
        AuthAccount other=createAccount(Role.CUSTOMER);
        long foreignService=serviceFor(other);
        long foreignTicket=workflow.create(new TelecomPrincipal(other),false,ticketForm(foreignService));
        MockHttpSession session=fullLogin(customer);
        mvc.perform(get("/customer/tickets/"+foreignTicket).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/customer/tickets/new").session(session).param("serviceId",String.valueOf(foreignService)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/customer/tickets").session(session).with(csrf()).param("serviceId",String.valueOf(foreignService))
                .param("category","OTHER").param("priority","LOW").param("severity","LOW").param("description","Foreign service"))
                .andExpect(status().isNotFound());
        assertThat(workflow.tickets(new TelecomPrincipal(customer),false,null,null,0).getItems()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket WHERE service_id=?",foreignService)).isEqualTo(1);
    }

    @Test void deskCreatesForCustomerButOtherRolesAndMissingCsrfAreDenied() throws Exception {
        long serviceId=serviceFor(customer);
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        MockHttpSession session=fullLogin(desk);
        String number=jdbc.queryForObject("SELECT customer_number FROM customer WHERE customer_id=?",String.class,customer.getCustomerId());
        mvc.perform(get("/service-desk/services").session(session)).andExpect(status().isOk());
        mvc.perform(get("/service-desk/services").session(session).param("customerNumber",number)).andExpect(status().isOk());
        mvc.perform(post("/service-desk/tickets").session(session).param("serviceId",String.valueOf(serviceId)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/service-desk/tickets").session(session).with(csrf()).param("serviceId",String.valueOf(serviceId))
                .param("category","OTHER").param("priority","LOW").param("severity","LOW").param("description","Raised by desk"))
                .andExpect(status().is3xxRedirection());
        long id=jdbc.queryForObject("SELECT ticket_id FROM trouble_ticket WHERE service_id=?",Long.class,serviceId);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND customer_id=?",id,customer.getCustomerId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND actor_user_id=?",id,desk.getUserId())).isEqualTo(1);
        mvc.perform(get("/service-desk/tickets/"+id).session(session)).andExpect(status().isOk());
        mvc.perform(get("/service-desk/tickets").session(fullLogin(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/customer/services").session(fullLogin(createAccount(Role.NETWORK_ENGINEER)))).andExpect(status().isForbidden());
    }

    @Test void validatesFormsInactiveServicesFiltersAndPaging() throws Exception {
        long serviceId=serviceFor(customer);
        MockHttpSession session=fullLogin(customer);
        mvc.perform(post("/customer/tickets").session(session).with(csrf()).param("serviceId",String.valueOf(serviceId))
                .param("category","INVALID").param("description"," ")).andExpect(status().isOk())
                .andExpect(model().hasErrors()).andExpect(view().name("tickets/new"));
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket WHERE service_id=?",serviceId)).isZero();
        mvc.perform(get("/customer/tickets").session(session).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/customer/tickets").session(session).param("status","INVALID")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE telecom_service SET service_status='SUSPENDED' WHERE service_id=?",serviceId);
        assertThatThrownBy(() -> workflow.create(new TelecomPrincipal(customer),false,ticketForm(serviceId)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"history","notification","audit"})
    void downstreamFailureRollsBackAllTicketWrites(String failure) {
        long serviceId=serviceFor(customer);
        long ticketsBefore=count("SELECT COUNT(*) FROM trouble_ticket");
        long historyBefore=count("SELECT COUNT(*) FROM ticket_status_history");
        long notificationBefore=count("SELECT COUNT(*) FROM notification");
        long auditBefore=count("SELECT COUNT(*) FROM audit_event");
        DataAccessResourceFailureException error=new DataAccessResourceFailureException("test write failure");
        if ("history".equals(failure)) { doThrow(error).when(ticketWrites).initialHistory(anyLong(),anyLong(),any(Instant.class)); }
        if ("notification".equals(failure)) { doThrow(error).when(ticketWrites).notifyCustomer(anyLong(),anyLong(),anyString(),any(Instant.class)); }
        if ("audit".equals(failure)) { doThrow(error).when(ticketWrites).audit(anyLong(),anyLong(),any(Instant.class)); }
        assertThatThrownBy(() -> workflow.create(new TelecomPrincipal(customer),false,ticketForm(serviceId)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket")).isEqualTo(ticketsBefore);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history")).isEqualTo(historyBefore);
        assertThat(count("SELECT COUNT(*) FROM notification")).isEqualTo(notificationBefore);
        assertThat(count("SELECT COUNT(*) FROM audit_event")).isEqualTo(auditBefore);
    }

    @Test void numbersStayUniqueUnderConcurrentCreationAndPaginationIsStable() throws Exception {
        long serviceId=serviceFor(customer);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Long>> tasks=new ArrayList<>();
            for(int i=0;i<22;i++) { tasks.add(() -> workflow.create(new TelecomPrincipal(customer),false,ticketForm(serviceId))); }
            java.util.Set<Long> ids=new java.util.HashSet<>();
            for(Future<Long> result:pool.invokeAll(tasks)) { ids.add(result.get(30,TimeUnit.SECONDS)); }
            assertThat(ids).hasSize(22);
            assertThat(count("SELECT COUNT(DISTINCT ticket_number) FROM trouble_ticket WHERE service_id=?",serviceId)).isEqualTo(22);
            com.amdocs.telecom.dto.ResultPage<com.amdocs.telecom.model.TroubleTicket> first=workflow.tickets(new TelecomPrincipal(customer),false,null,null,0);
            com.amdocs.telecom.dto.ResultPage<com.amdocs.telecom.model.TroubleTicket> second=workflow.tickets(new TelecomPrincipal(customer),false,null,null,1);
            assertThat(first.getItems()).hasSize(20); assertThat(first.isNext()).isTrue();
            assertThat(second.getItems()).hasSize(2); assertThat(second.isNext()).isFalse();
            assertThat(first.getItems()).extracting(com.amdocs.telecom.model.TroubleTicket::getTicketId)
                    .doesNotContainAnyElementsOf(second.getItems().stream().map(com.amdocs.telecom.model.TroubleTicket::getTicketId).collect(java.util.stream.Collectors.toList()));
        } finally { pool.shutdownNow(); }
    }

    @Test void ownershipUsesCurrentProfileAndLatestImmutableSlaVersion() {
        long serviceId=serviceFor(customer);
        int version=jdbc.queryForObject("SELECT MAX(policy_version)+1 FROM sla_configuration WHERE priority='CRITICAL'",Integer.class);
        jdbc.update("INSERT INTO sla_configuration (priority,policy_version,response_minutes,resolution_minutes) VALUES ('CRITICAL',?,10,60)",version);
        com.amdocs.telecom.dto.TicketCreateForm form=ticketForm(serviceId);
        form.setPriority(com.amdocs.telecom.model.TicketPriority.CRITICAL);
        long id=workflow.create(new TelecomPrincipal(customer),false,form);
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getSlaPolicyVersion()).isEqualTo(version);
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getResponseDeadline()).isEqualTo(clock.instant().plusSeconds(600));
        jdbc.update("UPDATE customer SET user_id=NULL WHERE customer_id=?",customer.getCustomerId());
        assertThatThrownBy(() -> workflow.ticket(new TelecomPrincipal(customer),false,id))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
    }

    @Test void auditIsAppendOnlyAndV3ChecksumIsVerified() throws Exception {
        long id=workflow.create(new TelecomPrincipal(customer),false,ticketForm(serviceFor(customer)));
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET action='ALTERED' WHERE ticket_id=?",id))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE ticket_id=?",id))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        String checksum=jdbc.queryForObject("SELECT checksum FROM schema_migration WHERE version=3",String.class);
        assertThat(checksum).isEqualTo(DatabaseSetup.checksum(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("db/migrations/V003__ticket_intake.sql"))));
        jdbc.update("UPDATE schema_migration SET checksum='wrong' WHERE version=3");
        try { assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(IllegalStateException.class); }
        finally { jdbc.update("UPDATE schema_migration SET checksum=? WHERE version=3",checksum); }
        assertThat(DatabaseMigrate.upgrade(source)).isFalse();
    }

    private long assignmentTicket() {
        long service=serviceFor(customer);
        jdbc.update("UPDATE telecom_service SET region=? WHERE service_id=?", "R-"+UUID.randomUUID().toString().substring(0,12),service);
        return workflow.create(new TelecomPrincipal(customer),false,ticketForm(service));
    }
    private String ticketRegion(long id) {
        return jdbc.queryForObject("SELECT s.region FROM telecom_service s JOIN trouble_ticket t ON t.service_id=s.service_id WHERE t.ticket_id=?",String.class,id);
    }
    private long eligible(long id,String code,int experience) {
        return jdbc.queryForObject("INSERT INTO network_engineer (employee_code,engineer_name,specialization,region,experience_years) "
                +"VALUES (?,'Assignment Test','IP Network',?,?) RETURNING engineer_id",Long.class,
                code+"_"+UUID.randomUUID().toString().substring(0,12),ticketRegion(id),experience);
    }
    private com.amdocs.telecom.dto.AssignmentForm assignment(long engineer,long version,String reason) {
        com.amdocs.telecom.dto.AssignmentForm form=new com.amdocs.telecom.dto.AssignmentForm();
        form.setEngineerId(engineer); form.setRowVersion(version); form.setReason(reason); return form;
    }

    @Test void recommendationsMatchSkillRegionAvailabilityAndRankActualWorkload() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket();
        long busy=eligible(id,"BUSY",50),b=eligible(id,"B",5),a=eligible(id,"A",5),junior=eligible(id,"JUNIOR",1);
        long unavailable=eligible(id,"UNAVAILABLE",99),wrongRegion=eligible(id,"REGION",99),wrongSkill=eligible(id,"SKILL",99);
        jdbc.update("UPDATE network_engineer SET available=FALSE WHERE engineer_id=?",unavailable);
        jdbc.update("UPDATE network_engineer SET region='Other' WHERE engineer_id=?",wrongRegion);
        jdbc.update("UPDATE network_engineer SET specialization='Other' WHERE engineer_id=?",wrongSkill);
        long workload=workflow.create(new TelecomPrincipal(customer),false,ticketForm(workflow.ticket(new TelecomPrincipal(customer),false,id).getServiceId()));
        assignments.assign(new TelecomPrincipal(desk),workload,assignment(busy,0,"Initial"));
        assertThat(assignments.options(new TelecomPrincipal(desk),id).getRecommendations())
                .extracting(value -> value.getEngineer().getEngineerId()).containsExactly(a,b,junior);
        // Terminal work must not contribute to the derived active workload.
        jdbc.update("UPDATE trouble_ticket SET status='CANCELLED' WHERE ticket_id=?",workload);
        jdbc.update("INSERT INTO ticket_status_history(ticket_id,old_status,new_status,changed_by,changed_at) VALUES (?,'ASSIGNED','CANCELLED','test-fixture',?)",workload,java.sql.Timestamp.from(clock.instant()));
        assertThat(assignments.options(new TelecomPrincipal(desk),id).getRecommendations())
                .extracting(value -> value.getEngineer().getEngineerId()).containsExactly(busy,a,b);
    }

    @Test void deskAssignsThroughUiAndCustomerCanSeeEngineerWithoutChangingSla() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(), engineer=eligible(id,"ASSIGN",6);
        MockHttpSession session=fullLogin(desk);
        String path="/service-desk/tickets/"+id+"/assignment";
        mvc.perform(get("/service-desk/queue").session(session)).andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus",com.amdocs.telecom.model.TicketStatus.OPEN));
        mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(view().name("tickets/assignment"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Active tickets")));
        mvc.perform(post(path).session(session).param("engineerId",String.valueOf(engineer)).param("rowVersion","0"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).session(session).with(csrf()).param("engineerId",String.valueOf(engineer)).param("rowVersion","0").param("reason","Initial routing"))
                .andExpect(redirectedUrl("/service-desk/tickets/"+id));
        com.amdocs.telecom.model.TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getStatus()).isEqualTo(com.amdocs.telecom.model.TicketStatus.ASSIGNED);
        assertThat(ticket.getAssignedEngineerId()).isEqualTo(engineer); assertThat(ticket.getRowVersion()).isEqualTo(1);
        assertThat(ticket.getFirstResponseAt()).isNull();
        assertThat(ticket.getResponseDeadline()).isEqualTo(ticket.getCreatedAt().plusSeconds(480*60));
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND event_type='TICKET_ASSIGNED'",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND engineer_id=? AND customer_id IS NULL",id,engineer)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND action='TICKET_ASSIGNED' AND new_engineer_id=?",id,engineer)).isEqualTo(1);
        mvc.perform(get("/customer/tickets/"+id).session(fullLogin(customer))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ASSIGN_")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Reassign engineer"))));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"ASSIGNED","IN_PROGRESS","PENDING_CUSTOMER"})
    void reassignmentPreservesOperationalStatusAndRecordsEngineerChangesInAudit(String operationalStatus) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(), first=eligible(id,"FIRST",5), second=eligible(id,"SECOND",5);
        assignments.assign(new TelecomPrincipal(desk),id,assignment(first,0,"Initial"));
        if(!"ASSIGNED".equals(operationalStatus)) {
            jdbc.update("UPDATE trouble_ticket SET status=CAST(? AS ticket_status),first_response_at=? WHERE ticket_id=?",operationalStatus,java.sql.Timestamp.from(clock.instant()),id);
            jdbc.update("INSERT INTO ticket_status_history(ticket_id,old_status,new_status,changed_by,changed_at) VALUES (?,'ASSIGNED',CAST(? AS ticket_status),'test-fixture',?)",id,operationalStatus,java.sql.Timestamp.from(clock.instant()));
        }
        long before=count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id);
        assignments.assign(new TelecomPrincipal(desk),id,assignment(second,1,"Region cover"));
        com.amdocs.telecom.model.TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getStatus().name()).isEqualTo(operationalStatus);
        assertThat(ticket.getRowVersion()).isEqualTo(2); assertThat(ticket.getAssignedEngineerId()).isEqualTo(second);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND action='TICKET_REASSIGNED' AND previous_engineer_id=? AND new_engineer_id=? AND reason='Region cover'",id,first,second)).isEqualTo(1);
        assertThat(assignments.options(new TelecomPrincipal(desk),id).getRecommendations()).extracting(value -> value.getEngineer().getEngineerId()).doesNotContain(second);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"unavailable","region","skill","missing","stale"})
    void assignmentRevalidatesEligibilityAndVersionAtSaveTime(String failure) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(), target=eligible(id,"VALIDATE",6);
        assertThat(assignments.options(new TelecomPrincipal(desk),id).getRecommendations()).hasSize(1);
        if("unavailable".equals(failure)) { jdbc.update("UPDATE network_engineer SET available=FALSE WHERE engineer_id=?",target); }
        if("region".equals(failure)) { jdbc.update("UPDATE network_engineer SET region='Other' WHERE engineer_id=?",target); }
        if("skill".equals(failure)) { jdbc.update("UPDATE network_engineer SET specialization='Other' WHERE engineer_id=?",target); }
        long submitted="missing".equals(failure) ? Long.MAX_VALUE : target;
        long version="stale".equals(failure) ? 7 : 0;
        assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(submitted,version,"Initial")))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class)
                .satisfies(error -> assertThat(((com.amdocs.telecom.exception.WorkflowException)error).getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getAssignedEngineerId()).isNull();
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=?",id)).isEqualTo(1);
    }

    @Test void noMatchIsExplicitAndOtherRolesCannotAssign() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket();
        String path="/service-desk/tickets/"+id+"/assignment";
        mvc.perform(get(path).session(fullLogin(desk))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No matching available engineers")));
        for(Role role:new Role[]{Role.CUSTOMER,Role.NETWORK_ENGINEER,Role.NETWORK_MANAGER}) {
            AuthAccount actor=role==Role.CUSTOMER ? customer : createAccount(role);
            MockHttpSession session=fullLogin(actor);
            mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
            mvc.perform(post(path).session(session).with(csrf()).param("engineerId","1").param("rowVersion","0")).andExpect(status().isForbidden());
            assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(actor),id,assignment(1,0,"Test")))
                    .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"history","notification","audit"})
    void failedAssignmentWritesRollBackStatusEngineerVersionAndAllRelatedRows(String failure) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(),target=eligible(id,"ROLLBACK",6);
        DataAccessResourceFailureException error=new DataAccessResourceFailureException("test assignment failure");
        if("history".equals(failure)) { doThrow(error).when(assignmentWrites).statusHistory(anyLong(),anyLong(),any(Instant.class)); }
        if("notification".equals(failure)) { doThrow(error).when(assignmentWrites).notifications(anyLong(),anyLong(),anyLong(),anyString(),any(Instant.class)); }
        if("audit".equals(failure)) { doThrow(error).when(assignmentWrites).audit(any(com.amdocs.telecom.model.TroubleTicket.class),anyLong(),anyLong(),any(),any(Instant.class)); }
        assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(target,0,"Initial"))).isInstanceOf(DataAccessResourceFailureException.class);
        com.amdocs.telecom.model.TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getAssignedEngineerId()).isNull(); assertThat(ticket.getRowVersion()).isZero();
        assertThat(ticket.getStatus()).isEqualTo(com.amdocs.telecom.model.TicketStatus.OPEN);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=?",id)).isEqualTo(1);
    }

    @Test void concurrentDeskAssignmentsHaveOneWinnerAndOneConflict() throws Exception {
        AuthAccount a=createAccount(Role.SERVICE_DESK),b=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(),one=eligible(id,"ONE",5),two=eligible(id,"TWO",5);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            List<Callable<String>> tasks=new ArrayList<>();
            tasks.add(() -> { start.await(); return assignmentOutcome(a,id,one); });
            tasks.add(() -> { start.await(); return assignmentOutcome(b,id,two); });
            Future<String> first=pool.submit(tasks.get(0)),second=pool.submit(tasks.get(1)); start.countDown();
            assertThat(java.util.Arrays.asList(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","conflict");
            assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getRowVersion()).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(2);
            assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND event_type='TICKET_ASSIGNED'",id)).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    private String assignmentOutcome(AuthAccount desk,long id,long engineer) {
        try { assignments.assign(new TelecomPrincipal(desk),id,assignment(engineer,0,"Concurrent")); return "saved"; }
        catch(com.amdocs.telecom.exception.WorkflowException error) {
            if(error.getStatus()==org.springframework.http.HttpStatus.CONFLICT) { return "conflict"; }
            throw error;
        }
    }

    @Test void rejectsSameEngineerMissingReasonAndStaleBrowserSubmission() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(),one=eligible(id,"FIRST",5),two=eligible(id,"SECOND",5);
        assignments.assign(new TelecomPrincipal(desk),id,assignment(one,0,"Initial"));
        assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(one,1,"No change")))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("different engineer");
        assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(two,1,"  ")))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("reason");
        mvc.perform(post("/service-desk/tickets/"+id+"/assignment").session(fullLogin(desk)).with(csrf())
                .param("engineerId",String.valueOf(two)).param("rowVersion","0").param("reason","Stale form"))
                .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.containsString("ticket changed")));
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getRowVersion()).isEqualTo(1);
    }

    @Test void missingOrChangedCategoryRuleHasNoFallbackEngineer() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(),target=eligible(id,"RULE",5);
        String skill=jdbc.queryForObject("SELECT specialization FROM category_specialization WHERE category='NO_CONNECTIVITY'",String.class);
        try {
            jdbc.update("UPDATE category_specialization SET specialization='Different' WHERE category='NO_CONNECTIVITY'");
            assertThat(assignments.options(new TelecomPrincipal(desk),id).getRecommendations()).isEmpty();
            assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(target,0,"Rule changed")))
                    .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
            jdbc.update("DELETE FROM category_specialization WHERE category='NO_CONNECTIVITY'");
            assertThat(assignments.options(new TelecomPrincipal(desk),id).getSpecialization()).isNull();
            assertThat(assignments.options(new TelecomPrincipal(desk),id).getRecommendations()).isEmpty();
            assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(target,0,"Missing rule")))
                    .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        } finally {
            jdbc.update("INSERT INTO category_specialization(category,specialization) VALUES ('NO_CONNECTIVITY',?) "
                    +"ON CONFLICT (category) DO UPDATE SET specialization=EXCLUDED.specialization",skill);
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"RESOLVED","CLOSED","CANCELLED","ESCALATED"})
    void completedCancelledAndEscalatedTicketsCannotBeAssigned(String blocked) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(),one=eligible(id,"FIRST",5),two=eligible(id,"SECOND",5);
        assignments.assign(new TelecomPrincipal(desk),id,assignment(one,0,"Initial"));
        if("RESOLVED".equals(blocked) || "CLOSED".equals(blocked)) {
            jdbc.update("UPDATE trouble_ticket SET status=CAST(? AS ticket_status),first_response_at=?,resolved_at=?,closed_at=? WHERE ticket_id=?",
                    blocked,java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()),
                    "CLOSED".equals(blocked) ? java.sql.Timestamp.from(clock.instant()) : null,id);
        } else { jdbc.update("UPDATE trouble_ticket SET status=CAST(? AS ticket_status) WHERE ticket_id=?",blocked,id); }
        assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(two,1,"Blocked state")))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("ticket status");
    }

    @Test void failedReassignmentAuditRestoresPreviousEngineerAndVersion() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        long id=assignmentTicket(),one=eligible(id,"FIRST",5),two=eligible(id,"SECOND",5);
        assignments.assign(new TelecomPrincipal(desk),id,assignment(one,0,"Initial"));
        doThrow(new DataAccessResourceFailureException("test reassignment failure")).when(assignmentWrites)
                .audit(any(com.amdocs.telecom.model.TroubleTicket.class),anyLong(),anyLong(),any(),any(Instant.class));
        assertThatThrownBy(() -> assignments.assign(new TelecomPrincipal(desk),id,assignment(two,1,"Reassign")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        com.amdocs.telecom.model.TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getAssignedEngineerId()).isEqualTo(one); assertThat(ticket.getRowVersion()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=?",id)).isEqualTo(2);
    }

    @Test void assignmentRecipientConstraintAndV4ChecksumAreEnforced() throws Exception {
        long id=assignmentTicket();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notification(ticket_id,event_type,message,created_at) VALUES (?,'TICKET_ASSIGNED','test',?)",
                id,java.sql.Timestamp.from(clock.instant()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        String checksum=jdbc.queryForObject("SELECT checksum FROM schema_migration WHERE version=4",String.class);
        assertThat(checksum).isEqualTo(DatabaseSetup.checksum(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("db/migrations/V004__engineer_assignment.sql"))));
        jdbc.update("UPDATE schema_migration SET checksum='invalid' WHERE version=4");
        try { assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(IllegalStateException.class); }
        finally { jdbc.update("UPDATE schema_migration SET checksum=? WHERE version=4",checksum); }
        assertThat(DatabaseMigrate.upgrade(source)).isFalse();
    }

    @Test void simultaneousReassignmentsCanSwapEngineersWithoutDeadlock() throws Exception {
        AuthAccount a=createAccount(Role.SERVICE_DESK),b=createAccount(Role.SERVICE_DESK);
        long first=assignmentTicket(),one=eligible(first,"FIRST",5),two=eligible(first,"SECOND",5);
        long second=workflow.create(new TelecomPrincipal(customer),false,ticketForm(workflow.ticket(new TelecomPrincipal(customer),false,first).getServiceId()));
        assignments.assign(new TelecomPrincipal(a),first,assignment(one,0,"Initial"));
        assignments.assign(new TelecomPrincipal(b),second,assignment(two,0,"Initial"));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<?> x=pool.submit(() -> { try { start.await(); } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                assignments.assign(new TelecomPrincipal(a),first,assignment(two,1,"Cover")); });
            Future<?> y=pool.submit(() -> { try { start.await(); } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                assignments.assign(new TelecomPrincipal(b),second,assignment(one,1,"Cover")); });
            start.countDown(); x.get(20,TimeUnit.SECONDS); y.get(20,TimeUnit.SECONDS);
            assertThat(workflow.ticket(new TelecomPrincipal(customer),false,first).getAssignedEngineerId()).isEqualTo(two);
            assertThat(workflow.ticket(new TelecomPrincipal(customer),false,second).getAssignedEngineerId()).isEqualTo(one);
        } finally { pool.shutdownNow(); }
    }

    private LifecycleForm action(TicketAction action,long version) {
        LifecycleForm form=new LifecycleForm(); form.setAction(action); form.setRowVersion(version);
        form.setReason("Customer diagnostics / verified fix"); form.setRootCause("Incorrect routing configuration");
        form.setResolutionText("Restored routing and verified connectivity <script>test</script>");
        form.setResolutionCode(ResolutionCode.CONFIGURATION_FIXED); return form;
    }
    private FeedbackForm feedback(long version,int rating) {
        FeedbackForm form=new FeedbackForm(); form.setRowVersion(version); form.setRating(rating); form.setComments("Support was helpful <b>thank you</b>"); return form;
    }
    private long assignedTo(AuthAccount desk,AuthAccount engineer) {
        long id=assignmentTicket();
        jdbc.update("UPDATE network_engineer SET region=? WHERE engineer_id=?",ticketRegion(id),engineer.getEngineerId());
        assignments.assign(new TelecomPrincipal(desk),id,assignment(engineer.getEngineerId(),0,"Initial"));
        return id;
    }
    private long currentVersion(long id) { return jdbc.queryForObject("SELECT row_version FROM trouble_ticket WHERE ticket_id=?",Long.class,id); }
    private void lifecyclePost(MockHttpSession session,String base,long id,TicketAction action) throws Exception {
        mvc.perform(get(base+"/tickets/"+id+"/actions").session(session).param("action",action.name()))
                .andExpect(status().isOk()).andExpect(view().name("tickets/action"));
        mvc.perform(post(base+"/tickets/"+id+"/actions").session(session).with(csrf())
                .param("rowVersion",String.valueOf(currentVersion(id))).param("action",action.name())
                .param("reason","Customer diagnostics / verified fix").param("rootCause","Incorrect routing configuration")
                .param("resolutionText","Restored routing <script>test</script>").param("resolutionCode","CONFIGURATION_FIXED"))
                .andExpect(redirectedUrl(base+"/tickets/"+id));
    }
    private long closedTicket(AuthAccount desk,AuthAccount engineer) {
        long id=assignedTo(desk,engineer);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.RESOLVE,2));
        lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,id,action(TicketAction.CLOSE,3));
        return id;
    }

    @Test void completeManualLifecycleWorksThroughWebFormsAndPreservesSlaAndFirstResponse() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,engineer);
        MockHttpSession engineerSession=fullLogin(engineer),deskSession=fullLogin(desk),customerSession=fullLogin(customer);
        mvc.perform(get("/engineer/tickets").session(engineerSession)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(workflow.ticket(new TelecomPrincipal(customer),false,id).getTicketNumber())));
        mvc.perform(get("/engineer/tickets/"+id).session(engineerSession)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Acknowledge / start work")));
        clock.advance(Duration.ofMinutes(5));
        lifecyclePost(engineerSession,"/engineer",id,TicketAction.ACKNOWLEDGE);
        Instant firstResponse=clock.instant();
        clock.advance(Duration.ofMinutes(1)); lifecyclePost(engineerSession,"/engineer",id,TicketAction.WAIT_CUSTOMER);
        clock.advance(Duration.ofMinutes(2)); lifecyclePost(engineerSession,"/engineer",id,TicketAction.RESUME);
        clock.advance(Duration.ofMinutes(3)); lifecyclePost(engineerSession,"/engineer",id,TicketAction.RESOLVE);
        assertThat(count("SELECT COUNT(*) FROM ticket_resolution WHERE ticket_id=? AND resolver_engineer_id=?",id,engineer.getEngineerId())).isEqualTo(1);
        clock.advance(Duration.ofMinutes(1)); lifecyclePost(deskSession,"/service-desk",id,TicketAction.CLOSE);
        TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED); assertThat(ticket.getFirstResponseAt()).isEqualTo(firstResponse);
        assertThat(ticket.getResolvedAt()).isBeforeOrEqualTo(ticket.getClosedAt());
        assertThat(ticket.getResponseDeadline()).isEqualTo(ticket.getCreatedAt().plusSeconds(480*60));
        assertThat(ticket.getResolutionDeadline()).isEqualTo(ticket.getCreatedAt().plusSeconds(2880*60));
        mvc.perform(get("/customer/tickets/"+id+"/feedback").session(customerSession)).andExpect(status().isOk());
        mvc.perform(post("/customer/tickets/"+id+"/feedback").session(customerSession).with(csrf())
                .param("rowVersion",String.valueOf(ticket.getRowVersion())).param("rating","5").param("comments","Good support <b>thanks</b>"))
                .andExpect(redirectedUrl("/customer/tickets/"+id));
        mvc.perform(get("/customer/tickets/"+id).session(customerSession)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;b&gt;thanks&lt;/b&gt;")));
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM ticket_feedback WHERE ticket_id=? AND rating=5",id)).isEqualTo(1);
        assertThat(currentVersion(id)).isEqualTo(7);
        mvc.perform(get("/customer/tickets/"+id+"/feedback").session(customerSession)).andExpect(status().isConflict());
    }

    @Test void oldEngineerLosesReadAndWriteAccessAfterReassignmentAndAckIsNotReset() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),old=createAccount(Role.NETWORK_ENGINEER),next=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,old);
        jdbc.update("UPDATE network_engineer SET region=? WHERE engineer_id=?",ticketRegion(id),next.getEngineerId());
        lifecycle.act(new TelecomPrincipal(old),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        Instant first=workflow.ticket(new TelecomPrincipal(customer),false,id).getFirstResponseAt();
        MockHttpSession oldSession=fullLogin(old);
        assignments.assign(new TelecomPrincipal(desk),id,assignment(next.getEngineerId(),2,"Cover"));
        mvc.perform(get("/engineer/tickets/"+id).session(oldSession)).andExpect(status().isNotFound());
        mvc.perform(post("/engineer/tickets/"+id+"/actions").session(oldSession).with(csrf()).param("rowVersion","3").param("action","RESOLVE"))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(next),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,3)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getFirstResponseAt()).isEqualTo(first);
    }

    @Test void cancellationIsOwnedStateRestrictedAndReasonIsRetained() throws Exception {
        long id=assignmentTicket(); MockHttpSession session=fullLogin(customer);
        AuthAccount other=createAccount(Role.CUSTOMER);
        mvc.perform(get("/customer/tickets/"+id+"/actions").session(fullLogin(other)).param("action","CANCEL")).andExpect(status().isNotFound());
        mvc.perform(post("/customer/tickets/"+id+"/actions").session(session).param("action","CANCEL").param("rowVersion","0")).andExpect(status().isForbidden());
        LifecycleForm empty=action(TicketAction.CANCEL,0); empty.setReason(" ");
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(customer),Role.CUSTOMER,id,empty)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        lifecyclePost(session,"/customer",id,TicketAction.CANCEL);
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(count("SELECT COUNT(*) FROM ticket_cancellation WHERE ticket_id=? AND cancelled_by_user_id=?",id,customer.getUserId())).isEqualTo(1);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(customer),Role.CUSTOMER,id,action(TicketAction.CANCEL,1)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long assigned=assignedTo(desk,engineer);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(customer),Role.CUSTOMER,assigned,action(TicketAction.CANCEL,1)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,assigned,action(TicketAction.CANCEL,1));
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,assigned).getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test void requiresResolutionFieldsAndBlocksWrongRoleAndInvalidTransitions() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,engineer);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.RESOLVE,1)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,id,action(TicketAction.ACKNOWLEDGE,1)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        LifecycleForm form=action(TicketAction.RESOLVE,2); form.setRootCause(" "); form.setResolutionText(null); form.setResolutionCode(null);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,form))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("required");
        assertThat(count("SELECT COUNT(*) FROM ticket_resolution WHERE ticket_id=?",id)).isZero();
        assertThat(currentVersion(id)).isEqualTo(2);
        mvc.perform(post("/engineer/tickets/"+id+"/actions").session(fullLogin(engineer)).with(csrf())
                .param("rowVersion","1").param("action","WAIT_CUSTOMER").param("reason","Need logs"))
                .andExpect(status().isConflict());
        long legacy=jdbc.queryForObject("SELECT ticket_id FROM trouble_ticket WHERE ticket_number='TT-2026-000006'",Long.class);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,legacy,action(TicketAction.CLOSE,currentVersion(legacy))))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("Resolution details are missing");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"resolution","history","notification","audit"})
    void resolveFailureRollsBackMetadataStatusVersionAndRelatedWrites(String failure) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,engineer);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        long notifications=count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id),audit=count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=?",id);
        DataAccessResourceFailureException error=new DataAccessResourceFailureException("test lifecycle failure");
        if("resolution".equals(failure)) { doThrow(error).when(lifecycleWrites).resolution(anyLong(),anyLong(),any(LifecycleForm.class),any(Instant.class)); }
        if("history".equals(failure)) { doThrow(error).when(lifecycleWrites).history(any(TroubleTicket.class),any(TicketAction.class),anyLong(),any(),any(Instant.class)); }
        if("notification".equals(failure)) { doThrow(error).when(lifecycleWrites).notifications(any(TroubleTicket.class),anyLong(),anyString(),anyString(),any(Instant.class)); }
        if("audit".equals(failure)) { doThrow(error).when(lifecycleWrites).audit(anyLong(),anyLong(),anyString(),any(),any(Instant.class)); }
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.RESOLVE,2)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        TroubleTicket ticket=workflow.ticket(new TelecomPrincipal(customer),false,id);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS); assertThat(ticket.getResolvedAt()).isNull(); assertThat(ticket.getRowVersion()).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ticket_resolution WHERE ticket_id=?",id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id)).isEqualTo(notifications);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=?",id)).isEqualTo(audit);
    }

    @Test void concurrentFeedbackIsSingleSubmissionAndRatingAndOwnershipAreEnforced() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=closedTicket(desk,engineer); AuthAccount other=createAccount(Role.CUSTOMER);
        assertThatThrownBy(() -> lifecycle.feedback(new TelecomPrincipal(other),id,feedback(4,5))).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        mvc.perform(post("/customer/tickets/"+id+"/feedback").session(fullLogin(customer)).with(csrf()).param("rowVersion","4").param("rating","6"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<String> task=() -> { start.await(); try { lifecycle.feedback(new TelecomPrincipal(customer),id,feedback(4,5)); return "saved"; }
                catch(com.amdocs.telecom.exception.WorkflowException error) { if(error.getStatus()==org.springframework.http.HttpStatus.CONFLICT) { return "conflict"; } throw error; } };
            Future<String> first=pool.submit(task),second=pool.submit(task); start.countDown();
            assertThat(java.util.Arrays.asList(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","conflict");
        } finally { pool.shutdownNow(); }
        assertThat(count("SELECT COUNT(*) FROM ticket_feedback WHERE ticket_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(5);
        assertThatThrownBy(() -> jdbc.update("UPDATE ticket_feedback SET rating=1 WHERE ticket_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ticket_resolution WHERE ticket_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void cancellationAndFeedbackAuditFailureDoNotLeavePartialRecords() {
        long cancelId=assignmentTicket();
        doThrow(new DataAccessResourceFailureException("test cancellation rollback")).when(lifecycleWrites).audit(anyLong(),anyLong(),anyString(),any(),any(Instant.class));
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(customer),Role.CUSTOMER,cancelId,action(TicketAction.CANCEL,0))).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(count("SELECT COUNT(*) FROM ticket_cancellation WHERE ticket_id=?",cancelId)).isZero(); assertThat(currentVersion(cancelId)).isZero();
        doCallRealMethod().when(lifecycleWrites).audit(anyLong(),anyLong(),anyString(),any(),any(Instant.class));
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=closedTicket(desk,engineer);
        doThrow(new DataAccessResourceFailureException("test feedback rollback")).when(lifecycleWrites).audit(anyLong(),anyLong(),anyString(),any(),any(Instant.class));
        assertThatThrownBy(() -> lifecycle.feedback(new TelecomPrincipal(customer),id,feedback(4,5))).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(count("SELECT COUNT(*) FROM ticket_feedback WHERE ticket_id=?",id)).isZero(); assertThat(currentVersion(id)).isEqualTo(4);
    }

    @Test void currentEngineerProfileLinkIsRequiredAndFeedbackRequiresClosure() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,engineer); MockHttpSession session=fullLogin(engineer);
        assertThatThrownBy(() -> lifecycle.feedback(new TelecomPrincipal(customer),id,feedback(1,5)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("after ticket closure");
        jdbc.update("UPDATE network_engineer SET user_id=NULL WHERE engineer_id=?",engineer.getEngineerId());
        mvc.perform(get("/engineer/tickets/"+id).session(session)).andExpect(status().isForbidden());
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThat(currentVersion(id)).isEqualTo(1);
    }

    @Test void closureCannotBeRepeatedAndResolvedTicketsCannotBeCancelledOrReopened() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,engineer);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.RESOLVE,2));
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,id,action(TicketAction.CANCEL,3)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,id,action(TicketAction.CLOSE,3));
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(desk),Role.SERVICE_DESK,id,action(TicketAction.CLOSE,4)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.RESUME,4)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThat(workflow.ticket(new TelecomPrincipal(customer),false,id).getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    private com.amdocs.telecom.dto.SlaChangeForm slaEdit(long version,TicketPriority priority) {
        com.amdocs.telecom.dto.SlaChangeForm form=new com.amdocs.telecom.dto.SlaChangeForm();
        form.setRowVersion(version); form.setPriority(priority); form.setReason("Verified operational review"); return form;
    }
    private TroubleTicket owned(long id) { return workflow.ticket(new TelecomPrincipal(customer),false,id); }
    private void reachResponseWarning(long id) {
        TroubleTicket ticket=owned(id);
        clock.advance(Duration.between(clock.instant(),ticket.getCreatedAt().plus(Duration.between(ticket.getCreatedAt(),ticket.getResponseDeadline()).multipliedBy(80).dividedBy(100))));
    }

    @Test void warningBreachAndOverdueEscalationsAreDurableAndIdempotent() {
        long id=assignmentTicket(); TroubleTicket original=owned(id);
        reachResponseWarning(id);
        assertThat(escalations.inspect(id)).isEqualTo(1);
        assertThat(owned(id).getStatus()).isEqualTo(TicketStatus.ESCALATED);
        assertThat(slaWrites.state(id,false).getPreviousStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND escalation_id IS NOT NULL",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND actor_kind='SYSTEM' AND actor_user_id IS NULL",id)).isEqualTo(1);
        assertThat(escalations.inspect(id)).isZero();
        clock.advance(Duration.between(clock.instant(),original.getResponseDeadline()));
        assertThat(escalations.inspect(id)).isEqualTo(1); assertThat(escalations.inspect(id)).isZero();
        clock.advance(Duration.between(clock.instant(),original.getResolutionDeadline().plus(Duration.between(original.getCreatedAt(),original.getResolutionDeadline()))));
        assertThat(escalations.inspect(id)).isEqualTo(1); assertThat(escalations.inspect(id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM escalation_history WHERE ticket_id=?",id)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=? AND escalation_id IS NOT NULL",id)).isEqualTo(6);
        assertThat(owned(id).getResponseDeadline()).isEqualTo(original.getResponseDeadline());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM escalation_history WHERE ticket_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void catchUpRecordsAllDueLevelsButCompletedAndCancelledWorkDoesNotEscalate() {
        long id=assignmentTicket(); TroubleTicket ticket=owned(id);
        clock.advance(Duration.between(clock.instant(),ticket.getResolutionDeadline().plus(Duration.between(ticket.getCreatedAt(),ticket.getResolutionDeadline()))));
        assertThat(escalations.inspect(id)).isEqualTo(3); assertThat(slaWrites.state(id,false).getLevel()).isEqualTo(3);
        clock.reset();
        long cancelled=assignmentTicket();
        lifecycle.act(new TelecomPrincipal(customer),Role.CUSTOMER,cancelled,action(TicketAction.CANCEL,0));
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long closed=closedTicket(desk,engineer);
        long resolved=assignedTo(desk,engineer);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,resolved,action(TicketAction.ACKNOWLEDGE,1));
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,resolved,action(TicketAction.RESOLVE,2));
        clock.advance(Duration.ofDays(10));
        assertThat(escalations.inspect(cancelled)).isZero(); assertThat(escalations.inspect(closed)).isZero(); assertThat(escalations.inspect(resolved)).isZero();
    }

    @Test void explicitResumptionRestoresRecordedPendingStatusAndDoesNotResetSla() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long id=assignedTo(desk,engineer);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.WAIT_CUSTOMER,2));
        TroubleTicket original=owned(id);
        clock.advance(Duration.between(clock.instant(),original.getCreatedAt().plus(Duration.between(original.getCreatedAt(),original.getResolutionDeadline()).multipliedBy(80).dividedBy(100))));
        assertThat(escalations.inspect(id)).isEqualTo(1);
        assertThat(slaWrites.state(id,false).getPreviousStatus()).isEqualTo(TicketStatus.PENDING_CUSTOMER);
        assertThatThrownBy(() -> lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.WAIT_CUSTOMER,currentVersion(id))))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        MockHttpSession session=fullLogin(desk);
        mvc.perform(get("/service-desk/tickets/"+id+"/resume-escalation").session(session)).andExpect(status().isOk());
        mvc.perform(post("/service-desk/tickets/"+id+"/resume-escalation").session(session).with(csrf())
                .param("rowVersion",String.valueOf(currentVersion(id))).param("reason","Investigations resumed").param("status","OPEN"))
                .andExpect(redirectedUrl("/service-desk/tickets/"+id));
        assertThat(owned(id).getStatus()).isEqualTo(TicketStatus.PENDING_CUSTOMER);
        assertThat(owned(id).getFirstResponseAt()).isEqualTo(original.getFirstResponseAt());
        assertThat(owned(id).getResolutionDeadline()).isEqualTo(original.getResolutionDeadline());
        assertThat(escalations.inspect(id)).isZero();
        clock.advance(Duration.between(clock.instant(),original.getResolutionDeadline()));
        assertThat(escalations.inspect(id)).isEqualTo(1);
        assertThat(slaWrites.state(id,false).getPreviousStatus()).isEqualTo(TicketStatus.PENDING_CUSTOMER);
    }

    @Test void priorityChangesUseOriginalCreationLatestPolicyAndDoNotAddFakeStatusHistory() throws Exception {
        long id=assignmentTicket(); TroubleTicket original=owned(id);
        AuthAccount desk=createAccount(Role.SERVICE_DESK); MockHttpSession session=fullLogin(desk);
        int version=jdbc.queryForObject("SELECT MAX(policy_version)+1 FROM sla_configuration WHERE priority='HIGH'",Integer.class);
        jdbc.update("INSERT INTO sla_configuration(priority,policy_version,response_minutes,resolution_minutes) VALUES ('HIGH',?,20,180)",version);
        clock.advance(Duration.ofHours(2));
        mvc.perform(get("/service-desk/tickets/"+id+"/priority").session(session)).andExpect(status().isOk());
        mvc.perform(post("/service-desk/tickets/"+id+"/priority").session(session).with(csrf()).param("rowVersion","0").param("priority","HIGH").param("reason","Business impact"))
                .andExpect(redirectedUrl("/service-desk/tickets/"+id));
        TroubleTicket changed=owned(id);
        assertThat(changed.getSlaPolicyVersion()).isEqualTo(version); assertThat(changed.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(changed.getResponseDeadline()).isEqualTo(original.getCreatedAt().plusSeconds(20*60));
        assertThat(changed.getResolutionDeadline()).isEqualTo(original.getCreatedAt().plusSeconds(180*60));
        assertThat(slaService.evaluate(changed).getResponse().getState()).isEqualTo(com.amdocs.telecom.service.SlaEvaluation.State.BREACHED);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND action='PRIORITY_CHANGED' AND previous_priority='LOW' AND new_priority='HIGH' AND new_sla_version=?",id,version)).isEqualTo(1);
        mvc.perform(post("/service-desk/tickets/"+id+"/priority").session(session).with(csrf()).param("rowVersion","0").param("priority","MEDIUM").param("reason","Stale"))
                .andExpect(status().isConflict());
        assertThat(escalations.inspect(id)).isEqualTo(2);
    }

    @Test void slaViewsCountersAndDeskOnlyOperationsEnforceRolesAndCsrf() throws Exception {
        long id=assignmentTicket(); reachResponseWarning(id); escalations.inspect(id);
        AuthAccount manager=createAccount(Role.NETWORK_MANAGER),desk=createAccount(Role.SERVICE_DESK);
        MockHttpSession managerSession=fullLogin(manager),deskSession=fullLogin(desk),customerSession=fullLogin(customer);
        mvc.perform(get("/manager/sla").session(managerSession)).andExpect(status().isOk()).andExpect(view().name("tickets/sla"));
        mvc.perform(get("/service-desk/sla").session(deskSession)).andExpect(status().isOk());
        mvc.perform(get("/customer/tickets/"+id).session(customerSession)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Team Lead")));
        mvc.perform(get("/manager/sla").session(customerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/service-desk/tickets/"+id+"/priority").session(managerSession).with(csrf()).param("rowVersion",String.valueOf(currentVersion(id))).param("priority","HIGH").param("reason","Review"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/service-desk/tickets/"+id+"/resume-escalation").session(deskSession).param("rowVersion",String.valueOf(currentVersion(id))).param("reason","Review"))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> slaOperations.priority(new TelecomPrincipal(customer),id,slaEdit(currentVersion(id),TicketPriority.HIGH)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        java.util.Map<String,Object> counters=slaOperations.counters(new TelecomPrincipal(manager),Role.NETWORK_MANAGER);
        long response=0,resolution=0;
        for(TroubleTicket row:jdbc.query("SELECT * FROM trouble_ticket WHERE status NOT IN ('RESOLVED','CLOSED','CANCELLED')",new com.amdocs.telecom.dao.mapper.TroubleTicketRowMapper())) {
            com.amdocs.telecom.service.SlaEvaluation.Assessment assessed=slaService.evaluate(row);
            if(assessed.getResponse().active(com.amdocs.telecom.service.SlaEvaluation.State.BREACHED)) { response++; }
            if(assessed.getResolution().active(com.amdocs.telecom.service.SlaEvaluation.State.BREACHED)) { resolution++; }
        }
        assertThat(((Number)counters.get("response_breached")).longValue()).isEqualTo(response);
        assertThat(((Number)counters.get("resolution_breached")).longValue()).isEqualTo(resolution);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"history","notification","audit"})
    void escalationFailureRollsBackStatusStateHistoryAndPendingNotifications(String failure) {
        long id=assignmentTicket(); reachResponseWarning(id);
        DataAccessResourceFailureException error=new DataAccessResourceFailureException("test SLA failure");
        if("history".equals(failure)) { doThrow(error).when(slaWrites).statusHistory(any(TroubleTicket.class),any(TicketStatus.class),anyString(),anyString(),any(Instant.class)); }
        if("notification".equals(failure)) { doThrow(error).when(slaWrites).notifications(any(TroubleTicket.class),anyLong(),anyLong(),anyInt(),any(Instant.class)); }
        if("audit".equals(failure)) { doThrow(error).when(slaWrites).systemAudit(anyLong(),anyInt(),any(com.amdocs.telecom.service.SlaEvaluation.Assessment.class),any(Instant.class)); }
        assertThatThrownBy(() -> escalations.inspect(id)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(owned(id).getStatus()).isEqualTo(TicketStatus.OPEN); assertThat(currentVersion(id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM escalation_history WHERE ticket_id=?",id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM ticket_escalation_state WHERE ticket_id=?",id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(1);
    }

    @Test void overlappingChecksHaveOneEscalationAndMonitorCanRepeatSafely() throws Exception {
        long id=assignmentTicket(); reachResponseWarning(id);
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> job=() -> { start.await(); return escalations.inspect(id); };
            Future<Integer> first=pool.submit(job),second=pool.submit(job); start.countDown();
            assertThat(java.util.Arrays.asList(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(0,1);
        } finally { pool.shutdownNow(); }
        assertThat(count("SELECT COUNT(*) FROM escalation_history WHERE ticket_id=?",id)).isEqualTo(1);
        slaMonitor.runOnce();
        assertThat(slaMonitor.runOnce()).isZero();
        assertThat(count("SELECT COUNT(*) FROM escalation_history WHERE ticket_id=?",id)).isEqualTo(1);
    }

    @Test void priorityNotificationFailureRestoresOriginalPolicyDeadlinesAndAudit() {
        long id=assignmentTicket(); TroubleTicket original=owned(id);
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        doThrow(new DataAccessResourceFailureException("test priority notification failure")).when(lifecycleWrites)
                .notifications(any(TroubleTicket.class),anyLong(),anyString(),anyString(),any(Instant.class));
        assertThatThrownBy(() -> slaOperations.priority(new TelecomPrincipal(desk),id,slaEdit(0,TicketPriority.HIGH)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        TroubleTicket restored=owned(id);
        assertThat(restored.getPriority()).isEqualTo(original.getPriority());
        assertThat(restored.getSlaPolicyVersion()).isEqualTo(original.getSlaPolicyVersion());
        assertThat(restored.getResponseDeadline()).isEqualTo(original.getResponseDeadline());
        assertThat(restored.getResolutionDeadline()).isEqualTo(original.getResolutionDeadline());
        assertThat(currentVersion(id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE ticket_id=? AND action='PRIORITY_CHANGED'",id)).isZero();
    }

    @Test void resumptionAuditFailurePreservesEscalatedStatusAndRecordedPreviousState() {
        long id=assignmentTicket(); reachResponseWarning(id); escalations.inspect(id);
        AuthAccount desk=createAccount(Role.SERVICE_DESK); long version=currentVersion(id);
        long notifications=count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id);
        doThrow(new DataAccessResourceFailureException("test resumption audit failure")).when(lifecycleWrites)
                .audit(anyLong(),anyLong(),anyString(),any(),any(Instant.class));
        assertThatThrownBy(() -> slaOperations.resume(new TelecomPrincipal(desk),id,slaEdit(version,null)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(owned(id).getStatus()).isEqualTo(TicketStatus.ESCALATED);
        assertThat(currentVersion(id)).isEqualTo(version);
        assertThat(slaWrites.state(id,false).getPreviousStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(slaWrites.state(id,false).getLevel()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history WHERE ticket_id=?",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE ticket_id=?",id)).isEqualTo(notifications);
    }

    @Test void disabledRecipientsRetryWithoutPartialWritesAndUnknownLegacyStateIsNotGuessed() {
        long id=assignmentTicket(); reachResponseWarning(id);
        jdbc.update("UPDATE escalation_recipient SET enabled=FALSE WHERE escalation_level=1");
        try { assertThatThrownBy(() -> escalations.inspect(id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
            assertThat(owned(id).getStatus()).isEqualTo(TicketStatus.OPEN);
        } finally { jdbc.update("UPDATE escalation_recipient SET enabled=TRUE WHERE escalation_level=1"); }
        assertThat(escalations.inspect(id)).isEqualTo(1);
        long unknown=assignmentTicket(); jdbc.update("UPDATE trouble_ticket SET status='ESCALATED' WHERE ticket_id=?",unknown);
        AuthAccount desk=createAccount(Role.SERVICE_DESK);
        assertThatThrownBy(() -> slaOperations.resume(new TelecomPrincipal(desk),unknown,slaEdit(0,null)))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("recorded operational status");
        long legacy=jdbc.queryForObject("SELECT ticket_id FROM trouble_ticket WHERE ticket_number='TT-2026-000005'",Long.class);
        assertThat(slaWrites.state(legacy,false).getPreviousStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    private long notification(long ticket) { return jdbc.queryForObject("SELECT notification_id FROM notification WHERE ticket_id=? AND customer_id=? AND event_type='TICKET_CREATED'",Long.class,ticket,customer.getCustomerId()); }
    private long node(AuthAccount desk) {
        com.amdocs.telecom.dto.NetworkNodeForm form=new com.amdocs.telecom.dto.NetworkNodeForm();
        form.setCode("NODE-"+UUID.randomUUID()); form.setName("Test node"); form.setRegion("West");
        return networkEvents.node(new TelecomPrincipal(desk),form);
    }
    private com.amdocs.telecom.dto.NetworkEventForm eventForm(long node) {
        com.amdocs.telecom.dto.NetworkEventForm form=new com.amdocs.telecom.dto.NetworkEventForm();
        form.setNodeId(node); form.setEventKey(UUID.randomUUID()); form.setType(com.amdocs.telecom.dto.NetworkEventForm.Type.OUTAGE);
        form.setDescription("Outage <script>unsafe()</script>"); return form;
    }
    private String eventStatus(long id) { return jdbc.queryForObject("SELECT processing_status FROM network_event WHERE event_id=?",String.class,id); }

    @Test void notificationDeliveryIsIdempotentAndReadStateDoesNotChangeProcessing() throws Exception {
        long ticket=assignmentTicket(),id=notification(ticket); MockHttpSession session=fullLogin(customer);
        mvc.perform(get("/customer/notifications").session(session)).andExpect(status().isOk()).andExpect(view().name("notifications/inbox"));
        mvc.perform(post("/customer/notifications/"+id+"/read").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/customer/notifications/"+id+"/read").session(session).with(csrf())).andExpect(redirectedUrl("/customer/notifications"));
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM notification WHERE notification_id=?",String.class,id)).isEqualTo("PENDING");
        assertThat(notifications.process(id)).isTrue(); assertThat(notifications.process(id)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM notification_receipt WHERE notification_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification WHERE notification_id=? AND read_at IS NOT NULL AND delivery_status='DELIVERED' AND attempts=1",id)).isEqualTo(1);
        notifications.read(new TelecomPrincipal(customer),Role.CUSTOMER,id);
        assertThat(notifications.inbox(new TelecomPrincipal(customer),Role.CUSTOMER,true,0).getItems()).isEmpty();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM notification_receipt WHERE notification_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void notificationRetriesBoundFailuresAndRollBackPartialDeliveryReceiptsEvenForSqlErrors() {
        long id=notification(assignmentTicket());
        doAnswer(call -> { call.callRealMethod(); jdbc.execute("SELECT 1/0"); return null; }).when(notificationDelivery).deliver(eq(id),any(Instant.class));
        for(int attempt=1;attempt<=3;attempt++) {
            assertThat(notifications.process(id)).isFalse();
            assertThat(count("SELECT COUNT(*) FROM notification_receipt WHERE notification_id=?",id)).isZero();
            assertThat(count("SELECT attempts FROM notification WHERE notification_id=?",id)).isEqualTo(attempt);
            assertThat(notifications.process(id)).isFalse(); clock.advance(Duration.ofSeconds(60));
        }
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM notification WHERE notification_id=?",String.class,id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM notification WHERE notification_id=?",String.class,id)).isEqualTo("DELIVERY_UNAVAILABLE");
        assertThat(notifications.process(id)).isFalse();
    }

    @Test void concurrentNotificationWorkersPublishOneReceipt() throws Exception {
        long id=notification(assignmentTicket()); ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try { Callable<Boolean> job=() -> { start.await(); return notifications.process(id); };
            Future<Boolean> a=pool.submit(job),b=pool.submit(job); start.countDown();
            assertThat(java.util.Arrays.asList(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); }
        assertThat(count("SELECT COUNT(*) FROM notification_receipt WHERE notification_id=?",id)).isEqualTo(1);
        assertThat(count("SELECT attempts FROM notification WHERE notification_id=?",id)).isEqualTo(1);
    }

    @Test void inboxOwnershipManagerRecipientsAndDeskProcessingAreSeparated() throws Exception {
        long ticket=assignmentTicket(),id=notification(ticket); AuthAccount other=createAccount(Role.CUSTOMER),manager=createAccount(Role.NETWORK_MANAGER),desk=createAccount(Role.SERVICE_DESK);
        assertThat(notifications.inbox(new TelecomPrincipal(other),Role.CUSTOMER,false,0).getItems()).isEmpty();
        assertThatThrownBy(() -> notifications.read(new TelecomPrincipal(other),Role.CUSTOMER,id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThatThrownBy(() -> notifications.read(new TelecomPrincipal(desk),Role.SERVICE_DESK,id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        jdbc.update("UPDATE notification SET message='<script>unsafe()</script>' WHERE notification_id=?",id);
        mvc.perform(get("/customer/notifications").session(fullLogin(customer))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>unsafe"))));
        reachResponseWarning(ticket); escalations.inspect(ticket);
        long management=jdbc.queryForObject("SELECT notification_id FROM notification WHERE ticket_id=? AND escalation_recipient_level=1",Long.class,ticket);
        notifications.read(new TelecomPrincipal(manager),Role.NETWORK_MANAGER,management);
        assertThatThrownBy(() -> notifications.read(new TelecomPrincipal(manager),Role.NETWORK_MANAGER,id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        mvc.perform(get("/manager/notifications").session(fullLogin(manager))).andExpect(status().isOk());
        mvc.perform(get("/service-desk/notifications").session(fullLogin(desk))).andExpect(status().isOk());
    }

    @Test void engineerInboxRequiresCurrentProfileAndAssignment() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long ticket=assignedTo(desk,engineer);
        long id=jdbc.queryForObject("SELECT notification_id FROM notification WHERE ticket_id=? AND engineer_id=?",Long.class,ticket,engineer.getEngineerId());
        assertThat(notifications.inbox(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,false,0).getItems()).hasSize(1);
        mvc.perform(get("/engineer/notifications").session(fullLogin(engineer))).andExpect(status().isOk());
        AuthAccount replacement=createAccount(Role.NETWORK_ENGINEER);
        jdbc.update("UPDATE trouble_ticket SET assigned_engineer_id=? WHERE ticket_id=?",replacement.getEngineerId(),ticket);
        assertThat(notifications.inbox(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,false,0).getItems()).isEmpty();
        assertThatThrownBy(() -> notifications.read(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
    }

    @Test void notificationFailureDoesNotStopOtherJobsAndLegacyProcessingCanRecover() {
        long first=notification(assignmentTicket()),second=notification(assignmentTicket());
        doThrow(new DataAccessResourceFailureException("private SQL details")).when(notificationDelivery).deliver(eq(first),any(Instant.class));
        doReturn(java.util.Arrays.asList(first,second)).when(notificationWrites).pending(any(Instant.class));
        jdbc.update("UPDATE notification SET delivery_status='PROCESSING' WHERE notification_id=?",second);
        notifications.runOnce();
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM notification WHERE notification_id=?",String.class,first)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM notification WHERE notification_id=?",String.class,second)).isEqualTo("DELIVERED");
    }

    @Test void outageCreatesTicketsForMultipleServicesAtomicallyWithSystemProvenanceAndReplayProtection() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK); long node=node(desk),a=serviceFor(customer),b=serviceFor(createAccount(Role.CUSTOMER));
        for(long service:new long[]{a,b}) { networkEvents.link(new TelecomPrincipal(desk),node,jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,service)); }
        com.amdocs.telecom.dto.NetworkEventForm form=eventForm(node); long id=networkEvents.simulate(new TelecomPrincipal(desk),form);
        assertThat(networkEvents.simulate(new TelecomPrincipal(desk),form)).isEqualTo(id);
        assertThat(networkEvents.process(id)).isTrue(); assertThat(networkEvents.process(id)).isFalse();
        assertThat(eventStatus(id)).isEqualTo("PROCESSED"); assertThat(count("SELECT COUNT(*) FROM event_ticket WHERE event_id=?",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM event_ticket e JOIN trouble_ticket t USING(ticket_id) WHERE e.event_id=? AND t.priority='CRITICAL' AND t.category='NETWORK_OUTAGE' AND t.status='OPEN'",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM event_ticket e JOIN audit_event a USING(ticket_id) WHERE e.event_id=? AND a.actor_kind='SYSTEM' AND a.actor_user_id IS NULL",id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM event_ticket e JOIN ticket_status_history h USING(ticket_id) WHERE e.event_id=? AND h.changed_by=?",id,"SYSTEM:NETWORK_EVENT:"+id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM event_ticket e JOIN notification n USING(ticket_id) WHERE e.event_id=? AND n.event_type='TICKET_CREATED'",id)).isEqualTo(2);
        long ticket=jdbc.queryForObject("SELECT ticket_id FROM event_ticket WHERE event_id=? AND service_id=?",Long.class,id,a);
        int minutes=jdbc.queryForObject("SELECT p.response_minutes FROM trouble_ticket t JOIN sla_configuration p ON (p.priority,p.policy_version)=(t.priority,t.sla_policy_version) WHERE ticket_id=?",Integer.class,ticket);
        assertThat(owned(ticket).getResponseDeadline()).isEqualTo(owned(ticket).getCreatedAt().plusSeconds(minutes*60));
        form.setDescription("Different payload");
        assertThatThrownBy(() -> networkEvents.simulate(new TelecomPrincipal(desk),form)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE event_ticket SET service_id=? WHERE event_id=? AND service_id=?",b,id,a)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void unmappedAndInactiveServicesRemainForReviewAndCanBeRetriedAfterMapping() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK); long node=node(desk),service=serviceFor(customer);
        long event=networkEvents.simulate(new TelecomPrincipal(desk),eventForm(node)); networkEvents.process(event);
        assertThat(eventStatus(event)).isEqualTo("UNMAPPED");
        networkEvents.link(new TelecomPrincipal(desk),node,jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,service));
        jdbc.update("UPDATE customer SET status='INACTIVE' WHERE customer_id=?",customer.getCustomerId());
        networkEvents.retry(new TelecomPrincipal(desk),event); networkEvents.process(event); assertThat(eventStatus(event)).isEqualTo("UNMAPPED");
        jdbc.update("UPDATE customer SET status='ACTIVE' WHERE customer_id=?",customer.getCustomerId());
        networkEvents.retry(new TelecomPrincipal(desk),event); networkEvents.process(event); assertThat(eventStatus(event)).isEqualTo("PROCESSED");
        assertThat(count("SELECT COUNT(*) FROM event_ticket WHERE event_id=?",event)).isEqualTo(1);
        assertThatThrownBy(() -> networkEvents.retry(new TelecomPrincipal(desk),event)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"history","notification","audit","association"})
    void eventFailureRollsBackTicketsAndAllSideEffectsAndPersistsRetry(String failure) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK); long node=node(desk),service=serviceFor(customer);
        networkEvents.link(new TelecomPrincipal(desk),node,jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,service));
        long event=networkEvents.simulate(new TelecomPrincipal(desk),eventForm(node));
        DataAccessResourceFailureException error=new DataAccessResourceFailureException("test event failure");
        if("history".equals(failure)) { doThrow(error).when(ticketWrites).eventHistory(anyLong(),anyLong(),any(Instant.class)); }
        if("notification".equals(failure)) { doThrow(error).when(ticketWrites).notifyCustomer(anyLong(),anyLong(),anyString(),any(Instant.class)); }
        if("audit".equals(failure)) { doThrow(error).when(ticketWrites).eventAudit(anyLong(),anyLong(),any(Instant.class)); }
        if("association".equals(failure)) { doAnswer(call -> { jdbc.execute("SELECT 1/0"); return null; }).when(eventWrites).ticket(anyLong(),anyLong(),anyLong()); }
        for(int attempt=1;attempt<=3;attempt++) {
            assertThat(networkEvents.process(event)).isFalse();
            assertThat(count("SELECT COUNT(*) FROM trouble_ticket WHERE service_id=?",service)).isZero();
            assertThat(count("SELECT COUNT(*) FROM event_ticket WHERE event_id=?",event)).isZero();
            assertThat(count("SELECT attempts FROM network_event WHERE event_id=?",event)).isEqualTo(attempt);
            assertThat(networkEvents.process(event)).isFalse(); clock.advance(Duration.ofSeconds(60));
        }
        assertThat(eventStatus(event)).isEqualTo("FAILED");
    }

    @Test void failureOnSecondServiceRollsBackFirstServiceAndRetryCanCompleteBoth() {
        AuthAccount desk=createAccount(Role.SERVICE_DESK); long node=node(desk),a=serviceFor(customer),b=serviceFor(customer);
        for(long id:new long[]{a,b}) { networkEvents.link(new TelecomPrincipal(desk),node,jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,id)); }
        long event=networkEvents.simulate(new TelecomPrincipal(desk),eventForm(node));
        doAnswer(call -> { call.callRealMethod(); if(((Long)call.getArgument(1)).longValue()==b) { throw new DataAccessResourceFailureException("test second association failure"); } return null; })
                .when(eventWrites).ticket(eq(event),anyLong(),anyLong());
        assertThat(networkEvents.process(event)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket WHERE service_id IN (?,?)",a,b)).isZero();
        assertThat(count("SELECT COUNT(*) FROM event_ticket WHERE event_id=?",event)).isZero();
        doCallRealMethod().when(eventWrites).ticket(eq(event),anyLong(),anyLong());
        clock.advance(Duration.ofSeconds(60)); assertThat(networkEvents.process(event)).isTrue();
        assertThat(count("SELECT COUNT(*) FROM event_ticket WHERE event_id=?",event)).isEqualTo(2);
    }

    @Test void pendingPersistedEventsRecoverWithAFreshQueueAndConcurrentWorkersCannotDuplicateTickets() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK); long node=node(desk),service=serviceFor(customer);
        networkEvents.link(new TelecomPrincipal(desk),node,jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,service));
        long event=networkEvents.simulate(new TelecomPrincipal(desk),eventForm(node));
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try { Callable<Boolean> job=() -> { start.await(); return networkEvents.process(event); };
            Future<Boolean> a=pool.submit(job),b=pool.submit(job); start.countDown();
            assertThat(java.util.Arrays.asList(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); }
        assertThat(count("SELECT COUNT(*) FROM event_ticket WHERE event_id=?",event)).isEqualTo(1);
        long recovered=networkEvents.simulate(new TelecomPrincipal(desk),eventForm(node));
        jdbc.update("UPDATE network_event SET processing_status='PROCESSING',attempts=1 WHERE event_id=?",recovered);
        com.amdocs.telecom.service.EventDispatcher fresh=new com.amdocs.telecom.service.EventDispatcher(networkEvents); fresh.recover();
        for(int i=0;i<256 && !"PROCESSED".equals(eventStatus(recovered));i++) { fresh.processNext(); }
        assertThat(eventStatus(recovered)).isEqualTo("PROCESSED"); assertThat(count("SELECT attempts FROM network_event WHERE event_id=?",recovered)).isEqualTo(2);
    }

    @Test void networkPagesEnforceRolesCsrfValidationAndRenderEscapedDetails() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK); MockHttpSession session=fullLogin(desk),other=fullLogin(customer);
        mvc.perform(get("/service-desk/network/nodes").session(session)).andExpect(status().isOk());
        mvc.perform(post("/service-desk/network/nodes").session(session).with(csrf()).param("code","!").param("name","Node").param("region","West"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","code"));
        MvcResult node=mvc.perform(post("/service-desk/network/nodes").session(session).with(csrf()).param("code","WEB-"+UUID.randomUUID()).param("name","Web node").param("region","West"))
                .andExpect(status().is3xxRedirection()).andReturn();
        long nodeId=Long.parseLong(node.getResponse().getRedirectedUrl().replace("/service-desk/network/nodes/",""));
        long service=serviceFor(customer); String code=jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,service);
        mvc.perform(post("/service-desk/network/nodes/"+nodeId+"/services").session(session).with(csrf()).param("serviceCode",code)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/service-desk/network/nodes/"+nodeId).session(session)).andExpect(status().isOk());
        mvc.perform(get("/service-desk/network/simulate").session(session)).andExpect(status().isOk());
        mvc.perform(post("/service-desk/network/simulate").session(session).param("nodeId",String.valueOf(nodeId))).andExpect(status().isForbidden());
        MvcResult event=mvc.perform(post("/service-desk/network/simulate").session(session).with(csrf()).param("nodeId",String.valueOf(nodeId))
                .param("eventKey",UUID.randomUUID().toString()).param("type","DEGRADATION").param("description","<script>unsafe()</script>"))
                .andExpect(status().is3xxRedirection()).andReturn();
        long id=Long.parseLong(event.getResponse().getRedirectedUrl().replace("/service-desk/network/events/","")); networkEvents.process(id);
        mvc.perform(get("/service-desk/network/events/"+id).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        mvc.perform(get("/service-desk/network/events").session(session)).andExpect(status().isOk());
        mvc.perform(get("/service-desk/network/events").session(other)).andExpect(status().isForbidden());
        assertThatThrownBy(() -> networkEvents.simulate(new TelecomPrincipal(customer),eventForm(nodeId))).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThat(count("SELECT COUNT(*) FROM event_ticket e JOIN trouble_ticket t USING(ticket_id) WHERE e.event_id=? AND t.priority='HIGH' AND t.category='SLOW_DATA'",id)).isEqualTo(1);
    }

    private void at(String instant) { clock.advance(Duration.between(clock.instant(),Instant.parse(instant))); }
    private com.amdocs.telecom.dto.ReportForm reportForm(com.amdocs.telecom.report.ReportType type,int year) {
        com.amdocs.telecom.dto.ReportForm form=new com.amdocs.telecom.dto.ReportForm(); form.setType(type); form.setFormat(com.amdocs.telecom.dto.ReportForm.Format.CSV);
        form.setFrom(java.time.LocalDate.of(year,1,1)); form.setTo(java.time.LocalDate.of(year,1,1)); return form;
    }
    private com.amdocs.telecom.report.ReportJob awaitReport(AuthAccount actor,Role role,UUID id) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<limit) { com.amdocs.telecom.report.ReportJob job=reports.status(new TelecomPrincipal(actor),role,id); if(job.isTerminal()) { return job; } Thread.sleep(10); }
        throw new AssertionError("Report job did not finish");
    }
    private String cell(com.amdocs.telecom.report.ReportTable table,int row,String header) { return table.getRows().get(row).get(table.getHeaders().indexOf(header)); }

    @Test void dashboardsScopeCustomerEngineerAndStaffAggregatesAndRenderAllFourRoles() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER),manager=createAccount(Role.NETWORK_MANAGER),other=createAccount(Role.CUSTOMER);
        long id=assignedTo(desk,engineer);
        java.util.Map<String,Object> own=dashboards.view(new TelecomPrincipal(customer),Role.CUSTOMER);
        assertThat(((Number)((java.util.Map<?,?>)own.get("stats")).get("total")).longValue()).isEqualTo(1);
        assertThat(((Number)((java.util.Map<?,?>)dashboards.view(new TelecomPrincipal(other),Role.CUSTOMER).get("stats")).get("total")).longValue()).isZero();
        assertThat(((Number)((java.util.Map<?,?>)dashboards.view(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER).get("stats")).get("total")).longValue()).isEqualTo(1);
        for(AuthAccount actor:new AuthAccount[]{customer,desk,engineer,manager}) {
            mvc.perform(get(route(actor.getRoles().get(0))).session(fullLogin(actor))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Current operations")));
        }
        jdbc.update("UPDATE trouble_ticket SET assigned_engineer_id=? WHERE ticket_id=?",createAccount(Role.NETWORK_ENGINEER).getEngineerId(),id);
        assertThat(((Number)((java.util.Map<?,?>)dashboards.view(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER).get("stats")).get("total")).longValue()).isZero();
    }

    @Test void reportSqlProducesKnownIndependentSlaAndDurationTotalsForAllSevenTypes() {
        at("2033-01-01T12:00:00Z"); AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER);
        long a=assignedTo(desk,engineer),b=assignedTo(desk,engineer),pending=assignmentTicket(),cancelled=assignmentTicket();
        lifecycle.act(new TelecomPrincipal(customer),Role.CUSTOMER,cancelled,action(TicketAction.CANCEL,0));
        clock.advance(Duration.ofMinutes(10)); lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,a,action(TicketAction.ACKNOWLEDGE,1));
        clock.advance(Duration.ofMinutes(50)); lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,a,action(TicketAction.RESOLVE,2));
        clock.advance(Duration.ofMinutes(421)); lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,b,action(TicketAction.ACKNOWLEDGE,1));
        clock.advance(Duration.ofMinutes(2400)); lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,b,action(TicketAction.RESOLVE,2));
        clock.advance(Duration.ofMinutes(119));
        java.time.LocalDate date=java.time.LocalDate.of(2033,1,1);
        for(com.amdocs.telecom.report.ReportType type:com.amdocs.telecom.report.ReportType.values()) {
            com.amdocs.telecom.report.ReportTable table=reportQueries.generate(type,date,date,clock.instant()); assertThat(table.getHeaders()).isNotEmpty();
            if(type==com.amdocs.telecom.report.ReportType.SLA_COMPLIANCE) {
                assertThat(cell(table,0,"eligible_tickets")).isEqualTo("3");
                for(String header:new String[]{"response_met","response_late","response_open_overdue","resolution_met","resolution_late","resolution_open_overdue"}) { assertThat(cell(table,0,header)).isEqualTo("1"); }
                assertThat(cell(table,0,"response_completed_met_percent")).isEqualTo("50.00");
            }
            if(type==com.amdocs.telecom.report.ReportType.AVERAGE_RESOLUTION) { assertThat(cell(table,0,"average_resolution_minutes")).isEqualTo("1470.50"); assertThat(cell(table,0,"unresolved_overdue")).isEqualTo("1"); }
            if(type==com.amdocs.telecom.report.ReportType.INCIDENT_CATEGORY) { assertThat(cell(table,0,"tickets")).isEqualTo("4"); }
            if(type==com.amdocs.telecom.report.ReportType.CRITICAL_INCIDENT) { assertThat(table.getRows()).isEmpty(); }
        }
        assertThat(owned(pending).getResolvedAt()).isNull();
    }

    @Test void reportCreationDatesRespectKolkataMidnightAndCriticalExportsEscapeUserContent() {
        long service=serviceFor(customer); com.amdocs.telecom.dto.TicketCreateForm form=ticketForm(service); form.setPriority(TicketPriority.CRITICAL); form.setDescription(" =SUM(A1),\"quoted\"\nnew line");
        at("2033-12-31T18:29:59Z"); workflow.create(new TelecomPrincipal(customer),false,form);
        clock.advance(Duration.ofSeconds(1)); workflow.create(new TelecomPrincipal(customer),false,form);
        at("2034-01-01T18:29:59Z"); workflow.create(new TelecomPrincipal(customer),false,form);
        clock.advance(Duration.ofSeconds(1)); workflow.create(new TelecomPrincipal(customer),false,form);
        java.time.LocalDate date=java.time.LocalDate.of(2034,1,1);
        com.amdocs.telecom.report.ReportTable table=reportQueries.generate(com.amdocs.telecom.report.ReportType.CRITICAL_INCIDENT,date,date,clock.instant());
        assertThat(table.getRows()).hasSize(2);
        String csv=new String(com.amdocs.telecom.report.ReportEncoder.encode(table,com.amdocs.telecom.dto.ReportForm.Format.CSV),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=SUM(A1),\"\"quoted\"\"\nnew line\"");
    }

    @Test void reportJobsSupportAuthorizedWebRequestStatusDownloadAndDeletion() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),other=createAccount(Role.SERVICE_DESK),manager=createAccount(Role.NETWORK_MANAGER);
        MockHttpSession session=fullLogin(desk),foreign=fullLogin(other);
        mvc.perform(get("/service-desk/reports").session(session)).andExpect(status().isOk());
        mvc.perform(post("/service-desk/reports").session(session)).andExpect(status().isForbidden());
        MvcResult response=mvc.perform(post("/service-desk/reports").session(session).with(csrf()).param("type","TICKET_VOLUME").param("format","TXT").param("from","2026-10-15").param("to","2026-10-15"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String path=response.getResponse().getRedirectedUrl(); UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));
        assertThat(awaitReport(desk,Role.SERVICE_DESK,id).getStatus()).isEqualTo("COMPLETE");
        mvc.perform(get(path).session(session)).andExpect(status().isOk());
        mvc.perform(get(path+"/download").session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".txt")));
        mvc.perform(get(path+"/download").session(foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/manager/reports/"+id).session(fullLogin(manager))).andExpect(status().isNotFound());
        mvc.perform(get("/service-desk/reports").session(fullLogin(customer))).andExpect(status().isForbidden());
        mvc.perform(post(path+"/delete").session(session).with(csrf())).andExpect(redirectedUrl("/service-desk/reports"));
        mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
    }

    @Test void rawJdbcAssessmentCommitsRollsBackAndLeavesBusinessTablesUntouched() throws Exception {
        long tickets=count("SELECT COUNT(*) FROM trouble_ticket"),history=count("SELECT COUNT(*) FROM ticket_status_history");
        java.util.Map<String,Long> evidence=com.amdocs.telecom.assessment.RawJdbcAssessment.run(source);
        assertThat(evidence).containsEntry("tickets",2L).containsEntry("resolved",1L).containsEntry("average_minutes",30L)
                .containsEntry("created_year",2026L).containsEntry("customers_without_incidents",1L).containsEntry("full_rollback_verified",1L);
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket")).isEqualTo(tickets); assertThat(count("SELECT COUNT(*) FROM ticket_status_history")).isEqualTo(history);
    }

    @ParameterizedTest @EnumSource(ResolutionCode.class)
    void resolutionCatalogAcceptsCaseStudyAndHistoricalValues(ResolutionCode code) {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER); long id=assignedTo(desk,engineer);
        lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,action(TicketAction.ACKNOWLEDGE,1));
        LifecycleForm resolve=action(TicketAction.RESOLVE,2); resolve.setResolutionCode(code); lifecycle.act(new TelecomPrincipal(engineer),Role.NETWORK_ENGINEER,id,resolve);
        assertThat(jdbc.queryForObject("SELECT resolution_code FROM ticket_resolution WHERE ticket_id=?",String.class,id)).isEqualTo(code.name());
    }

    @Test void finalRehearsalRunsEventToResolutionFeedbackDeliveryAndAuthorizedReport() throws Exception {
        AuthAccount desk=createAccount(Role.SERVICE_DESK),engineer=createAccount(Role.NETWORK_ENGINEER),manager=createAccount(Role.NETWORK_MANAGER);
        long node=node(desk),service=serviceFor(customer);
        networkEvents.link(new TelecomPrincipal(desk),node,jdbc.queryForObject("SELECT service_code FROM telecom_service WHERE service_id=?",String.class,service));
        jdbc.update("UPDATE network_engineer SET specialization='Core Network',region='West' WHERE engineer_id=?",engineer.getEngineerId());
        MockHttpSession deskSession=fullLogin(desk),engineerSession=fullLogin(engineer),customerSession=fullLogin(customer),managerSession=fullLogin(manager);
        MvcResult event=mvc.perform(post("/service-desk/network/simulate").session(deskSession).with(csrf())
                .param("eventKey",UUID.randomUUID().toString()).param("nodeId",String.valueOf(node)).param("type","OUTAGE").param("description","Final delivery rehearsal"))
                .andExpect(status().is3xxRedirection()).andReturn();
        long eventId=Long.parseLong(event.getResponse().getRedirectedUrl().replace("/service-desk/network/events/",""));
        assertThat(networkEvents.process(eventId)).isTrue();
        long ticket=jdbc.queryForObject("SELECT ticket_id FROM event_ticket WHERE event_id=?",Long.class,eventId);
        mvc.perform(post("/service-desk/tickets/"+ticket+"/assignment").session(deskSession).with(csrf())
                .param("rowVersion","0").param("engineerId",String.valueOf(engineer.getEngineerId())).param("reason","Final rehearsal"))
                .andExpect(redirectedUrl("/service-desk/tickets/"+ticket));
        lifecyclePost(engineerSession,"/engineer",ticket,TicketAction.ACKNOWLEDGE);
        lifecyclePost(engineerSession,"/engineer",ticket,TicketAction.RESOLVE);
        lifecyclePost(deskSession,"/service-desk",ticket,TicketAction.CLOSE);
        mvc.perform(post("/customer/tickets/"+ticket+"/feedback").session(customerSession).with(csrf()).param("rowVersion",String.valueOf(currentVersion(ticket))).param("rating","5").param("comments","Verified complete journey"))
                .andExpect(redirectedUrl("/customer/tickets/"+ticket));
        long notification=notification(ticket); assertThat(notifications.process(notification)).isTrue();
        mvc.perform(post("/customer/notifications/"+notification+"/read").session(customerSession).with(csrf())).andExpect(status().is3xxRedirection());
        com.amdocs.telecom.dto.ReportForm form=reportForm(com.amdocs.telecom.report.ReportType.CRITICAL_INCIDENT,2026);
        form.setFrom(java.time.LocalDate.of(2026,10,15)); form.setTo(form.getFrom()); UUID report=reports.request(new TelecomPrincipal(manager),Role.NETWORK_MANAGER,form);
        assertThat(awaitReport(manager,Role.NETWORK_MANAGER,report).getStatus()).isEqualTo("COMPLETE");
        MvcResult download=mvc.perform(get("/manager/reports/"+report+"/download").session(managerSession)).andExpect(status().isOk()).andReturn();
        assertThat(download.getResponse().getContentAsString()).contains(owned(ticket).getTicketNumber(),"CLOSED","Final delivery rehearsal");
        assertThat(count("SELECT COUNT(*) FROM ticket_feedback WHERE ticket_id=?",ticket)).isEqualTo(1);
        assertThat(networkEvents.process(eventId)).isFalse();
    }

    private AuthAccount createAccount(Role role) {
        String name = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("INSERT INTO app_user (username,display_name,email,password_hash) "
                        + "VALUES (?,?,?,?) RETURNING user_id", Long.class,
                name, "Test " + role.name(), name + "@example.test", encoder.encode(PASSWORD));
        jdbc.update("INSERT INTO user_role VALUES (?,?)", id, role.name());
        if (role == Role.CUSTOMER) {
            jdbc.update("INSERT INTO customer (customer_number,customer_name,email,mobile_number,customer_type,city,user_id) "
                            + "VALUES (?, 'Test Customer', ?, '+910000000099', 'CONSUMER', 'Pune', ?)",
                    name, name + "@example.test", id);
        } else if (role == Role.NETWORK_ENGINEER) {
            jdbc.update("INSERT INTO network_engineer (employee_code,engineer_name,specialization,region,user_id) "
                    + "VALUES (?, 'Test Engineer', 'IP Network', 'West', ?)", name, id);
        }
        return accounts.findById(id, false).get();
    }
    private PendingChallenge loginChallenge(AuthAccount user, String binding) {
        Optional<PendingChallenge> pending = authentication.beginLogin(user.getUsername(), PASSWORD, binding);
        assertThat(pending).isPresent();
        return pending.get();
    }
    private String code(PendingChallenge pending, String binding) {
        return delivery.preview(pending.getId(), binding).orElseThrow(AssertionError::new);
    }
    private String wrong(String real) { return "000000".equals(real) ? "111111" : "000000"; }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    private MvcResult captchaPage(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
    }
    private String answer(MvcResult page) {
        String question = (String) page.getModelAndView().getModel().get("captchaQuestion");
        String[] values = question.split(" \\+ ");
        return Integer.toString(Integer.parseInt(values[0]) + Integer.parseInt(values[1]));
    }
    private MockHttpSession fullLogin(AuthAccount user) throws Exception {
        MvcResult page = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", user.getUsername())
                        .param("password", PASSWORD).param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page)))
                .andExpect(redirectedUrl("/login/otp"));
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        PendingChallenge pending = SessionSecrets.pending(session);
        String otp = code(pending, SessionSecrets.binding(session));
        String originalSessionId = session.getId();
        mvc.perform(post("/login/otp").session(session).with(csrf()).param("code", otp))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(session.getId()).isNotEqualTo(originalSessionId);
        assertThat(SessionSecrets.pending(session)).isNull();
        return session;
    }

    @Test void loginAndSharedAssetsRenderWithoutAuthentication() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("CAPTCHA")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(get("/forgot-password")).andExpect(status().isOk());
        mvc.perform(get("/webjars/bootstrap/5.3.3/css/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @ParameterizedTest @EnumSource(Role.class)
    void completeLoginReachesOnlyTheAuthorizedRoleWorkspace(Role role) throws Exception {
        AuthAccount user = role == Role.CUSTOMER ? customer : createAccount(role);
        MockHttpSession session = fullLogin(user);
        String own = route(role);
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl(own));
        mvc.perform(get(own).session(session)).andExpect(status().isOk()).andExpect(view().name("dashboard"));
        for (Role other : Role.values()) {
            if (other != role) { mvc.perform(get(route(other)).session(session)).andExpect(status().isForbidden()); }
        }
        assertThat(count("SELECT COUNT(*) FROM login_history WHERE user_id=? AND outcome='SUCCESS'", user.getUserId())).isEqualTo(1);
    }

    @Test void partialLoginDoesNotGrantAccessAndCsrfIsRequired() throws Exception {
        MvcResult page = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/login").session(session).param("username", customer.getUsername()).param("password", PASSWORD)
                        .param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page))).andExpect(status().isForbidden());
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                        .param("password", PASSWORD).param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page))).andExpect(redirectedUrl("/login/otp"));
        mvc.perform(get("/customer/dashboard").session(session)).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(post("/login/otp").session(session).param("code", "123456")).andExpect(status().isForbidden());
    }

    @Test void firstLoginFormStillWorksAfterAnotherLoginAndRecoveryPageLoad() throws Exception {
        MvcResult first = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) first.getRequest().getSession();
        mvc.perform(get("/login").session(session)).andExpect(status().isOk());
        mvc.perform(get("/forgot-password").session(session)).andExpect(status().isOk());
        String id = (String) first.getModelAndView().getModel().get("captchaId");
        assertThat(first.getResponse().getContentAsString()).contains("name=\"captchaId\" value=\"" + id + "\"");
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                        .param("password", PASSWORD).param("captchaId", id).param("captcha", answer(first)))
                .andExpect(redirectedUrl("/login/otp"));
        assertThat(SessionSecrets.pending(session)).isNotNull();
    }

    @Test void incorrectCaptchaDoesNotIssueOtpAndHistoryRecordsTheFailure() throws Exception {
        MvcResult page = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                        .param("password", PASSWORD).param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", "999"))
                .andExpect(status().isOk()).andExpect(view().name("auth/login"))
                .andExpect(model().attribute("message", "CAPTCHA answer was incorrect or expired. Enter the sum of the new numbers shown below and submit again."));
        assertThat(SessionSecrets.pending(session)).isNull();
        assertThat(count("SELECT COUNT(*) FROM otp_challenge WHERE user_id=?", customer.getUserId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM login_history WHERE attempted_username=? AND outcome='CAPTCHA_FAILURE'",
                customer.getUsername())).isEqualTo(1);
    }

    @Test void passwordFailuresLockAtFiveAndUnlockAtFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            assertThat(authentication.beginLogin(customer.getUsername(), "wrong-password", BINDING)).isEmpty();
        }
        AuthAccount locked = accounts.findById(customer.getUserId(), false).get();
        assertThat(locked.getFailedAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isEqualTo(clock.instant().plusSeconds(900));
        assertThat(authentication.beginLogin(customer.getUsername(), PASSWORD, BINDING)).isEmpty();
        clock.advance(Duration.ofMinutes(15));
        assertThat(authentication.beginLogin(customer.getUsername(), PASSWORD, BINDING)).isPresent();
    }

    @Test void concurrentFailuresAreNotLost() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                attempts.add(() -> authentication.beginLogin(customer.getUsername(), "wrong-password", BINDING).isPresent());
            }
            for (Future<Boolean> result : executor.invokeAll(attempts)) { assertThat(result.get(10, TimeUnit.SECONDS)).isFalse(); }
            assertThat(accounts.findById(customer.getUserId(), false).get().getFailedAttempts()).isEqualTo(5);
        } finally { executor.shutdownNow(); }
    }

    @Test void otpIsHashedSingleUseAndBoundToItsSessionAndPurpose() {
        PendingChallenge pending = loginChallenge(customer, BINDING);
        String otp = code(pending, BINDING);
        OtpRecord stored = accounts.findChallenge(pending.getId(), false).get();
        assertThat(stored.getCodeHash()).isNotEqualTo(otp);
        assertThat(encoder.matches(otp, stored.getCodeHash())).isTrue();
        assertThat(authentication.completeLogin(pending, BINDING + "other", otp)).isEmpty();
        assertThat(authentication.resetPassword(pending, BINDING, otp, NEW_PASSWORD)).isFalse();
        TelecomPrincipal principal = authentication.completeLogin(pending, BINDING, otp).get();
        assertThat(principal.getUserId()).isEqualTo(customer.getUserId());
        assertThat(principal.getPassword()).isEmpty();
        assertThat(authentication.completeLogin(pending, BINDING, otp)).isEmpty();
        assertThat(delivery.preview(pending.getId(), BINDING)).isEmpty();
    }

    @Test void fiveWrongOtpAttemptsConsumeTheChallenge() {
        PendingChallenge pending = loginChallenge(customer, BINDING);
        String real = code(pending, BINDING);
        for (int i = 0; i < 5; i++) { assertThat(authentication.completeLogin(pending, BINDING, wrong(real))).isEmpty(); }
        assertThat(authentication.completeLogin(pending, BINDING, real)).isEmpty();
        assertThat(accounts.findChallenge(pending.getId(), false).get().getAttempts()).isEqualTo(5);
    }

    @Test void otpExpiresExactlyAtFiveMinutesAndReissueInvalidatesOldCode() {
        PendingChallenge first = loginChallenge(customer, BINDING);
        String old = code(first, BINDING);
        PendingChallenge replacement = loginChallenge(customer, BINDING + "second");
        assertThat(authentication.completeLogin(first, BINDING, old)).isEmpty();
        String code = code(replacement, BINDING + "second");
        clock.advance(Duration.ofMinutes(5));
        assertThat(authentication.completeLogin(replacement, BINDING + "second", code)).isEmpty();
    }

    @Test void resetChangesTheHashUnlocksAndRevokesSessionsAndOldChallenges() throws Exception {
        MockHttpSession authenticatedSession = fullLogin(customer);
        PendingChallenge oldLogin = loginChallenge(customer, BINDING);
        String oldOtp = code(oldLogin, BINDING);
        PendingChallenge reset = authentication.beginReset(customer.getUsername(), BINDING + "reset");
        String resetCode = code(reset, BINDING + "reset");
        assertThat(authentication.completeLogin(reset, BINDING + "reset", resetCode)).isEmpty();
        assertThat(authentication.resetPassword(reset, BINDING + "reset", resetCode, NEW_PASSWORD)).isTrue();
        assertThat(authentication.resetPassword(reset, BINDING + "reset", resetCode, NEW_PASSWORD)).isFalse();
        AuthAccount changed = accounts.findById(customer.getUserId(), false).get();
        assertThat(changed.getAuthVersion()).isEqualTo(customer.getAuthVersion() + 1);
        assertThat(encoder.matches(NEW_PASSWORD, changed.getPasswordHash())).isTrue();
        assertThat(changed.getPasswordHash()).isNotEqualTo(NEW_PASSWORD);
        assertThat(changed.getFailedAttempts()).isZero();
        assertThat(changed.getLockedUntil()).isNull();
        assertThat(authentication.completeLogin(oldLogin, BINDING, oldOtp)).isEmpty();
        mvc.perform(get("/customer/dashboard").session(authenticatedSession)).andExpect(redirectedUrl("/login?expired"));
        assertThat(authentication.beginLogin(customer.getUsername(), PASSWORD, BINDING)).isEmpty();
        assertThat(authentication.beginLogin(customer.getUsername(), NEW_PASSWORD, BINDING)).isPresent();
    }

    @Test void resetCanRecoverALockedAccountAndRejectsWeakPassword() {
        for (int i = 0; i < 5; i++) { authentication.beginLogin(customer.getUsername(), "wrong", BINDING); }
        PendingChallenge pending = authentication.beginReset(customer.getUsername(), BINDING);
        String otp = code(pending, BINDING);
        assertThat(authentication.resetPassword(pending, BINDING, otp, "short")).isFalse();
        assertThat(authentication.resetPassword(pending, BINDING, otp, NEW_PASSWORD)).isTrue();
        assertThat(authentication.beginLogin(customer.getUsername(), NEW_PASSWORD, BINDING)).isPresent();
    }

    @Test void resetScreensAndUnknownAccountResponseAreGeneric() throws Exception {
        MvcResult page = captchaPage("/forgot-password");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/forgot-password").session(session).with(csrf()).param("username", "does-not-exist")
                .param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page))).andExpect(redirectedUrl("/reset-password"));
        mvc.perform(get("/reset-password").session(session)).andExpect(status().isOk()).andExpect(view().name("auth/reset"));
        assertThat(authentication.beginReset("does-not-exist", BINDING).getPurpose()).isEqualTo(OtpPurpose.RESET);
    }

    @Test void resetDeliveryFailureDoesNotRevealWhetherAnAccountExists() {
        doThrow(new OtpDeliveryUnavailableException()).when(devDelivery)
                .deliver(any(UUID.class), anyString(), anyString(), any(Instant.class));
        PendingChallenge known = authentication.beginReset(customer.getUsername(), BINDING);
        PendingChallenge unknown = authentication.beginReset("does-not-exist", BINDING);
        assertThat(known.getPurpose()).isEqualTo(unknown.getPurpose());
        assertThat(accounts.findChallenge(known.getId(), false)).isEmpty();
        assertThat(accounts.findChallenge(unknown.getId(), false)).isEmpty();
    }

    @Test void resetFormChecksConfirmationAndCanCompleteReset() throws Exception {
        MvcResult page = captchaPage("/forgot-password");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/forgot-password").session(session).with(csrf()).param("username", customer.getUsername())
                .param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page))).andExpect(redirectedUrl("/reset-password"));
        PendingChallenge pending = SessionSecrets.pending(session);
        String otp = code(pending, SessionSecrets.binding(session));
        mvc.perform(post("/reset-password").session(session).with(csrf()).param("code", otp)
                .param("password", NEW_PASSWORD).param("confirmation", "Different-Password123!"))
                .andExpect(status().isOk()).andExpect(view().name("auth/reset"));
        mvc.perform(post("/reset-password").session(session).with(csrf()).param("code", otp)
                .param("password", NEW_PASSWORD).param("confirmation", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login"));
        assertThat(encoder.matches(NEW_PASSWORD, accounts.findById(customer.getUserId(), false).get().getPasswordHash())).isTrue();
    }

    @Test void developmentInboxCannotBeReadFromAnotherSessionOrByGuessingAnId() throws Exception {
        MvcResult page = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                .param("password", PASSWORD).param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page))).andExpect(redirectedUrl("/login/otp"));
        PendingChallenge pending = SessionSecrets.pending(session);
        String otp = code(pending, SessionSecrets.binding(session));
        mvc.perform(get("/dev/otp").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(otp)))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/dev/otp").param("id", pending.getId().toString())).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login/otp").session(session)).andExpect(status().isOk()).andExpect(view().name("auth/otp"))
                .andExpect(model().attribute("devCode", otp))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Development verification code")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/login/otp"));
        assertThat(SessionSecrets.pending(session).getId()).isEqualTo(pending.getId());
        mvc.perform(get("/forgot-password").session(session)).andExpect(status().isOk());
        assertThat(SessionSecrets.pending(session).getId()).isEqualTo(pending.getId());
        mvc.perform(get("/login/otp")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login/restart").session(session)).andExpect(status().isForbidden());
        assertThat(SessionSecrets.pending(session)).isNotNull();
        mvc.perform(post("/login/restart").session(session).with(csrf())).andExpect(redirectedUrl("/login"));
        assertThat(SessionSecrets.pending(session)).isNull();
        mvc.perform(get("/login").session(session)).andExpect(status().isOk());
    }

    @Test void expiredPendingVerificationAllowsFreshLoginForm() throws Exception {
        MvcResult page = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                .param("password", PASSWORD).param("captchaId", (String) page.getModelAndView().getModel().get("captchaId"))
                .param("captcha", answer(page))).andExpect(redirectedUrl("/login/otp"));
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andExpect(view().name("auth/login"));
        assertThat(SessionSecrets.pending(session)).isNull();
    }

    @Test void logoutRequiresCsrfAndEndsTheSession() throws Exception {
        MockHttpSession session = fullLogin(customer);
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/customer/dashboard").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/customer/dashboard")).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test void disabledUsersAndChangedRolesLoseExistingAccess() throws Exception {
        MockHttpSession session = fullLogin(customer);
        jdbc.update("UPDATE app_user SET enabled=FALSE WHERE user_id=?", customer.getUserId());
        mvc.perform(get("/customer/dashboard").session(session)).andExpect(redirectedUrl("/login?expired"));
        assertThat(authentication.beginLogin(customer.getUsername(), PASSWORD, BINDING)).isEmpty();
        AuthAccount manager = createAccount(Role.NETWORK_MANAGER);
        MockHttpSession managerSession = fullLogin(manager);
        jdbc.update("DELETE FROM user_role WHERE user_id=?", manager.getUserId());
        mvc.perform(get("/manager/dashboard").session(managerSession)).andExpect(redirectedUrl("/login?expired"));
    }

    @Test void databaseErrorsAreNotReturnedInThePublicPage() throws Exception {
        MvcResult page = captchaPage("/login");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        doThrow(new DataAccessResourceFailureException("private-host password=super-secret SELECT * FROM app_user"))
                .when(accounts).findByUsername(customer.getUsername(), true);
        MvcResult response = mvc.perform(post("/login").session(session).with(csrf()).param("username", customer.getUsername())
                .param("password", PASSWORD).param("captchaId", (String) page.getModelAndView().getModel().get("captchaId")).param("captcha", answer(page)))
                .andExpect(status().isServiceUnavailable()).andExpect(view().name("error/503")).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("super-secret", "private-host", "SELECT *", "Exception");
    }

    @Test void failedSuccessAuditRollsBackOtpConsumption() {
        PendingChallenge pending = loginChallenge(customer, BINDING);
        String otp = code(pending, BINDING);
        doThrow(new DataAccessResourceFailureException("test audit failure"))
                .when(accounts).record(eq(customer.getUserId()), anyString(), eq("SUCCESS"), any(Instant.class));
        assertThatThrownBy(() -> authentication.completeLogin(pending, BINDING, otp))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(accounts.findChallenge(pending.getId(), false).get().getConsumedAt()).isNull();
        doCallRealMethod().when(accounts).record(eq(customer.getUserId()), anyString(), eq("SUCCESS"), any(Instant.class));
        assertThat(authentication.completeLogin(pending, BINDING, otp)).isPresent();
    }

    @Test void additiveMigrationIsIdempotentAndRefusesChangedChecksums() throws Exception {
        long ticketsBefore=count("SELECT COUNT(*) FROM trouble_ticket");
        long historyBefore=count("SELECT COUNT(*) FROM ticket_status_history");
        assertThat(DatabaseMigrate.upgrade(source)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket")).isEqualTo(ticketsBefore);
        assertThat(count("SELECT COUNT(*) FROM trouble_ticket WHERE ticket_number LIKE 'TT-2026-%'")).isEqualTo(8);
        String saved = jdbc.queryForObject("SELECT checksum FROM schema_migration WHERE version=2", String.class);
        jdbc.update("UPDATE schema_migration SET checksum='invalid' WHERE version=2");
        try { assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(IllegalStateException.class); }
        finally { jdbc.update("UPDATE schema_migration SET checksum=? WHERE version=2", saved); }
        assertThat(count("SELECT COUNT(*) FROM schema_migration")).isEqualTo(8);
        assertThat(count("SELECT COUNT(*) FROM ticket_status_history")).isEqualTo(historyBefore);
    }

    private String route(Role role) {
        switch (role) {
            case SERVICE_DESK: return "/service-desk/dashboard";
            case NETWORK_ENGINEER: return "/engineer/dashboard";
            case NETWORK_MANAGER: return "/manager/dashboard";
            default: return "/customer/dashboard";
        }
    }
}
