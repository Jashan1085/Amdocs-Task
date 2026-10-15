package com.amdocs.telecom.service;
import org.slf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlaScheduler implements ApplicationListener<ApplicationReadyEvent>,DisposableBean {
    private static final Logger LOG=LoggerFactory.getLogger(SlaScheduler.class);
    private final SlaMonitor monitor;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started=new AtomicBoolean();
    public SlaScheduler(SlaMonitor monitor,ScheduledExecutorService executor) { this.monitor=monitor; this.executor=executor; }
    @Override public void onApplicationEvent(ApplicationReadyEvent event) {
        if(started.compareAndSet(false,true)) {
            executor.scheduleWithFixedDelay(() -> { try { monitor.runOnce(); }
                catch(RuntimeException failure) { LOG.warn("SLA monitor: FAILURE; next scheduled run will retry."); } },60,60,TimeUnit.SECONDS);
        }
    }
    @Override public void destroy() {
        executor.shutdown();
        try { if(!executor.awaitTermination(5,TimeUnit.SECONDS)) { executor.shutdownNow(); } }
        catch(InterruptedException interrupted) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
