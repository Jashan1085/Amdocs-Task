package com.amdocs.telecom.service;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.slf4j.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackgroundJobs implements ApplicationListener<ApplicationReadyEvent>,DisposableBean {
    private static final Logger LOG=LoggerFactory.getLogger(BackgroundJobs.class);
    private final EventDispatcher dispatcher;
    private final NotificationService notifications;
    private final ExecutorService workers;
    private final ScheduledExecutorService timers;
    private final AtomicBoolean started=new AtomicBoolean();
    public BackgroundJobs(EventDispatcher dispatcher,NotificationService notifications,ExecutorService workers,ScheduledExecutorService timers) {
        this.dispatcher=dispatcher; this.notifications=notifications; this.workers=workers; this.timers=timers;
    }
    @Override public void onApplicationEvent(ApplicationReadyEvent event) {
        if(!started.compareAndSet(false,true)) { return; }
        for(int i=0;i<2;i++) { workers.execute(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try { dispatcher.processNext(); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }); }
        timers.scheduleWithFixedDelay(() -> {
            try { dispatcher.recover(); } catch(RuntimeException failure) { LOG.warn("Event recovery unavailable; next scan will retry."); }
        },0,15,TimeUnit.SECONDS);
        timers.scheduleWithFixedDelay(() -> {
            try { notifications.runOnce(); } catch(RuntimeException failure) { LOG.warn("Notification scan unavailable; next scan will retry."); }
        },5,5,TimeUnit.SECONDS);
    }
    @Override public void destroy() {
        timers.shutdownNow(); workers.shutdownNow();
        try { timers.awaitTermination(5,TimeUnit.SECONDS); workers.awaitTermination(5,TimeUnit.SECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
