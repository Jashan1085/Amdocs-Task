package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.JdbcSlaDAO;
import com.amdocs.telecom.dto.SlaWorkItem;
import com.amdocs.telecom.model.TicketPriority;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SlaMonitorTest {
    @Test void priorityQueueOrdersCriticalFirstThenStableTicketId() {
        EscalationQueue queue=new EscalationQueue();
        queue.addAll(Arrays.asList(new SlaWorkItem(1,TicketPriority.LOW),new SlaWorkItem(3,TicketPriority.CRITICAL),new SlaWorkItem(2,TicketPriority.CRITICAL),new SlaWorkItem(4,TicketPriority.HIGH)));
        assertThat(queue.poll().get().getTicketId()).isEqualTo(2);
        assertThat(queue.poll().get().getTicketId()).isEqualTo(3);
        assertThat(queue.poll().get().getTicketId()).isEqualTo(4);
        assertThat(queue.poll().get().getTicketId()).isEqualTo(1); assertThat(queue.poll()).isEmpty();
    }
    @Test void failedTicketDoesNotStopBatchAndCursorAdvancesWithoutOffset() {
        JdbcSlaDAO dao=mock(JdbcSlaDAO.class); EscalationService service=mock(EscalationService.class);
        when(dao.scan(0,0)).thenReturn(Arrays.asList(new SlaWorkItem(1,TicketPriority.CRITICAL),new SlaWorkItem(2,TicketPriority.LOW)));
        when(dao.scan(4,2)).thenReturn(Collections.emptyList());
        when(service.inspect(1)).thenThrow(new IllegalStateException("test failure")); when(service.inspect(2)).thenReturn(1);
        assertThat(new SlaMonitor(dao,service).runOnce()).isEqualTo(1); verify(service).inspect(2); verify(dao).scan(4,2);
    }
    @Test void schedulerStartsOnceSurvivesFailuresAndStopsExecutor() throws Exception {
        SlaMonitor monitor=mock(SlaMonitor.class); ScheduledExecutorService executor=mock(ScheduledExecutorService.class);
        SlaScheduler scheduler=new SlaScheduler(monitor,executor);
        scheduler.onApplicationEvent(null); scheduler.onApplicationEvent(null);
        ArgumentCaptor<Runnable> task=ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleWithFixedDelay(task.capture(),eq(60L),eq(60L),eq(TimeUnit.SECONDS));
        when(monitor.runOnce()).thenThrow(new IllegalStateException("test failure")).thenReturn(0);
        assertThatCode(() -> { task.getValue().run(); task.getValue().run(); }).doesNotThrowAnyException();
        when(executor.awaitTermination(5,TimeUnit.SECONDS)).thenReturn(false);
        scheduler.destroy(); verify(executor).shutdown(); verify(executor).shutdownNow();
    }
}
