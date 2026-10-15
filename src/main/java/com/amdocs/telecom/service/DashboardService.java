package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.security.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;
@Service
public class DashboardService {
    private final TicketWorkflowService workflow;
    private final JdbcDashboardDAO dao;
    private final JdbcSlaDAO slas;
    private final Clock clock;
    public DashboardService(TicketWorkflowService workflow,JdbcDashboardDAO dao,JdbcSlaDAO slas,Clock clock) { this.workflow=workflow; this.dao=dao; this.slas=slas; this.clock=clock; }
    public Map<String,Object> view(TelecomPrincipal principal,Role role) {
        AuthAccount actor=workflow.actor(principal,role,false);
        Long customer=role==Role.CUSTOMER ? actor.getCustomerId() : null,engineer=role==Role.NETWORK_ENGINEER ? actor.getEngineerId() : null;
        Map<String,Object> result=new HashMap<>(); result.put("stats",dao.summary(customer,engineer,clock.instant())); result.put("extra",dao.extras(customer,engineer,role));
        result.put("recent",dao.recent(customer,engineer));
        if(role==Role.SERVICE_DESK || role==Role.NETWORK_MANAGER) { result.put("slaCounters",slas.counters(clock.instant())); result.put("workload",dao.workload()); }
        return result;
    }
}
