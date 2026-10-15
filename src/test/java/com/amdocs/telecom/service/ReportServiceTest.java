package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.JdbcReportDAO;
import com.amdocs.telecom.dto.ReportForm;
import com.amdocs.telecom.report.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.support.MutableClock;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReportServiceTest {
    private final JdbcReportDAO dao=mock(JdbcReportDAO.class);
    private final TicketWorkflowService workflow=mock(TicketWorkflowService.class);
    private final MutableClock clock=new MutableClock();
    private final javax.validation.ValidatorFactory validators=javax.validation.Validation.buildDefaultValidatorFactory();
    private final ReportService service=new ReportService(dao,workflow,validators.getValidator(),clock);
    private final TelecomPrincipal actor=new TelecomPrincipal(new AuthAccount(1,"desk","Desk","",true,0,null,0,null,null,Arrays.asList(Role.SERVICE_DESK)));
    @AfterEach void close() { service.destroy(); validators.close(); }
    private ReportForm form() { ReportForm form=new ReportForm(); form.setType(ReportType.TICKET_VOLUME); form.setFormat(ReportForm.Format.CSV); form.setFrom(LocalDate.of(2026,10,1)); form.setTo(LocalDate.of(2026,10,15)); return form; }
    private ReportJob await(UUID id) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end) { ReportJob job=service.status(actor,Role.SERVICE_DESK,id); if(job.isTerminal()) { return job; } Thread.sleep(10); }
        throw new AssertionError("Report timed out");
    }
    @Test void completedExportsExpireAndRejectOtherUsersAndRoles() throws Exception {
        when(dao.generate(any(),any(),any(),any())).thenReturn(new ReportTable(Arrays.asList("tickets"),Arrays.asList(Arrays.asList("2"))));
        UUID id=service.request(actor,Role.SERVICE_DESK,form()); assertThat(await(id).getStatus()).isEqualTo("COMPLETE"); assertThat(service.download(actor,Role.SERVICE_DESK,id)).isNotEmpty();
        TelecomPrincipal other=new TelecomPrincipal(new AuthAccount(2,"other","Other","",true,0,null,0,null,null,Arrays.asList(Role.SERVICE_DESK)));
        assertThatThrownBy(() -> service.status(other,Role.SERVICE_DESK,id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        assertThatThrownBy(() -> service.request(actor,Role.CUSTOMER,form())).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        clock.advance(Duration.ofMinutes(31)); assertThatThrownBy(() -> service.download(actor,Role.SERVICE_DESK,id)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
    }
    @Test void boundedActiveJobsCanBeCancelledAndInvalidRangesAreRefused() throws Exception {
        CountDownLatch release=new CountDownLatch(1);
        when(dao.generate(any(),any(),any(),any())).thenAnswer(call -> { release.await(); return new ReportTable(Arrays.asList("empty"),Collections.emptyList()); });
        try {
            UUID first=service.request(actor,Role.SERVICE_DESK,form()); service.request(actor,Role.SERVICE_DESK,form()); service.request(actor,Role.SERVICE_DESK,form());
            assertThatThrownBy(() -> service.request(actor,Role.SERVICE_DESK,form())).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("capacity");
            service.delete(actor,Role.SERVICE_DESK,first); assertThatThrownBy(() -> service.status(actor,Role.SERVICE_DESK,first)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
            ReportForm bad=form(); bad.setTo(bad.getFrom().minusDays(1)); assertThatThrownBy(() -> service.request(actor,Role.SERVICE_DESK,bad)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class);
        } finally { release.countDown(); }
    }
    @Test void failedJobDoesNotStopNextCallableOrExposeItsCause() throws Exception {
        when(dao.generate(any(),any(),any(),any())).thenThrow(new IllegalStateException("private SQL secret")).thenReturn(new ReportTable(Arrays.asList("ok"),Collections.emptyList()));
        UUID failed=service.request(actor,Role.SERVICE_DESK,form()); assertThat(await(failed).getStatus()).isEqualTo("FAILED");
        assertThatThrownBy(() -> service.download(actor,Role.SERVICE_DESK,failed)).isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageNotContaining("secret");
        assertThat(await(service.request(actor,Role.SERVICE_DESK,form())).getStatus()).isEqualTo("COMPLETE");
    }
}
