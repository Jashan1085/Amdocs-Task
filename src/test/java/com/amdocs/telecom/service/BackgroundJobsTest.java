package com.amdocs.telecom.service;
import com.amdocs.telecom.dto.NetworkEventForm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BackgroundJobsTest {
    @Test void queueIsBoundedDeduplicatesAndRecoversPersistedJobsAfterFailure() throws Exception {
        NetworkEventService service=mock(NetworkEventService.class); EventDispatcher dispatcher=new EventDispatcher(service,1);
        assertThat(dispatcher.enqueue(1)).isTrue(); assertThat(dispatcher.enqueue(1)).isTrue(); assertThat(dispatcher.enqueue(2)).isFalse();
        when(service.process(1)).thenThrow(new IllegalStateException("test failure"));
        assertThat(dispatcher.processNext()).isTrue();
        when(service.pending()).thenReturn(Arrays.asList(2L)); dispatcher.recover(); assertThat(dispatcher.processNext()).isTrue(); verify(service).process(2);
        EventDispatcher restarted=new EventDispatcher(service,1); restarted.recover(); restarted.processNext(); verify(service,times(2)).process(2);
    }
    @Test void schedulerStartsOnceIsolatesScanFailuresAndStopsBothExecutors() throws Exception {
        EventDispatcher dispatcher=mock(EventDispatcher.class); NotificationService service=mock(NotificationService.class);
        ExecutorService workers=mock(ExecutorService.class); ScheduledExecutorService timers=mock(ScheduledExecutorService.class);
        BackgroundJobs jobs=new BackgroundJobs(dispatcher,service,workers,timers); jobs.onApplicationEvent(null); jobs.onApplicationEvent(null);
        verify(workers,times(2)).execute(any(Runnable.class));
        ArgumentCaptor<Runnable> recovery=ArgumentCaptor.forClass(Runnable.class),notifications=ArgumentCaptor.forClass(Runnable.class);
        verify(timers).scheduleWithFixedDelay(recovery.capture(),eq(0L),eq(15L),eq(TimeUnit.SECONDS));
        verify(timers).scheduleWithFixedDelay(notifications.capture(),eq(5L),eq(5L),eq(TimeUnit.SECONDS));
        doThrow(new IllegalStateException("test failure")).doNothing().when(dispatcher).recover();
        when(service.runOnce()).thenThrow(new IllegalStateException("test failure")).thenReturn(0);
        assertThatCode(() -> { recovery.getValue().run(); recovery.getValue().run(); notifications.getValue().run(); notifications.getValue().run(); }).doesNotThrowAnyException();
        jobs.destroy(); verify(workers).shutdownNow(); verify(timers).shutdownNow();
        verify(workers).awaitTermination(5,TimeUnit.SECONDS); verify(timers).awaitTermination(5,TimeUnit.SECONDS);
    }
    @Test void simulatorServiceRefusesUseOutsideDevelopmentEvenWithoutAWebController() {
        NetworkEventService service=new NetworkEventService(null,null,null,null,new MockEnvironment(),null,mock(org.springframework.transaction.PlatformTransactionManager.class));
        assertThatThrownBy(() -> service.simulate(null,new NetworkEventForm()))
                .isInstanceOf(com.amdocs.telecom.exception.WorkflowException.class).hasMessageContaining("only in development");
    }
}
