package com.amdocs.telecom.config;
import com.amdocs.telecom.service.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import java.util.concurrent.*;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name="telecom.jobs.enabled",havingValue="true",matchIfMissing=true)
public class BackgroundJobsConfiguration {
    @Bean public BackgroundJobs backgroundJobs(EventDispatcher dispatcher,NotificationService notifications) {
        ExecutorService workers=Executors.newFixedThreadPool(2,r -> { Thread t=new Thread(r,"network-event-worker"); t.setDaemon(true); return t; });
        ScheduledExecutorService timers=Executors.newScheduledThreadPool(2,r -> { Thread t=new Thread(r,"notification-event-scheduler"); t.setDaemon(true); return t; });
        return new BackgroundJobs(dispatcher,notifications,workers,timers);
    }
}
