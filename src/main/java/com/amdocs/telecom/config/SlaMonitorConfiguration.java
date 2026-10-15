package com.amdocs.telecom.config;
import com.amdocs.telecom.service.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import java.util.concurrent.*;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name="telecom.sla.monitor.enabled",havingValue="true",matchIfMissing=true)
public class SlaMonitorConfiguration {
    @Bean(destroyMethod="") public ScheduledExecutorService slaExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> { Thread thread=new Thread(task,"telecom-sla-monitor"); thread.setDaemon(true); return thread; });
    }
    @Bean public SlaScheduler slaScheduler(SlaMonitor monitor,ScheduledExecutorService slaExecutor) { return new SlaScheduler(monitor,slaExecutor); }
}
