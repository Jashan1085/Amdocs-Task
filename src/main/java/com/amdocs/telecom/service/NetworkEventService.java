package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.exception.WorkflowException;
import com.amdocs.telecom.security.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.*;
import java.util.*;

@Service
public class NetworkEventService {
    private final TicketWorkflowService workflow;
    private final JdbcNetworkEventDAO dao;
    private final JdbcTicketWorkflowDAO services;
    private final Validator validator;
    private final Environment environment;
    private final Clock clock;
    private final TransactionTemplate transaction,nested;
    public NetworkEventService(TicketWorkflowService workflow,JdbcNetworkEventDAO dao,JdbcTicketWorkflowDAO services,Validator validator,
            Environment environment,Clock clock,PlatformTransactionManager manager) {
        this.workflow=workflow; this.dao=dao; this.services=services; this.validator=validator; this.environment=environment; this.clock=clock;
        transaction=new TransactionTemplate(manager); nested=new TransactionTemplate(manager); nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
    public long node(TelecomPrincipal principal,NetworkNodeForm form) {
        validate(form);
        return transaction.execute(status -> { workflow.actor(principal,Role.SERVICE_DESK,true); long id=dao.node(form);
            if(id==0) { throw error(HttpStatus.CONFLICT,"Node code already exists."); } return id; });
    }
    public void link(TelecomPrincipal principal,long node,String code) {
        if(code==null || code.trim().isEmpty() || code.length()>100) { throw error(HttpStatus.BAD_REQUEST,"Enter an existing service code."); }
        transaction.execute(status -> {
            workflow.actor(principal,Role.SERVICE_DESK,true); requireNode(node,true);
            long service=dao.service(code.trim()).orElseThrow(NetworkEventService::missing);
            services.lockService(service).orElseThrow(NetworkEventService::missing);
            dao.link(node,service); return null;
        });
    }
    public long simulate(TelecomPrincipal principal,NetworkEventForm form) {
        if(!Arrays.asList(environment.getActiveProfiles()).contains("dev")) { throw error(HttpStatus.FORBIDDEN,"Simulation is available only in development."); }
        validate(form);
        return transaction.execute(status -> {
            AuthAccount actor=workflow.actor(principal,Role.SERVICE_DESK,true); requireNode(form.getNodeId(),false);
            long id=dao.insert(form,actor.getUserId(),clock.instant());
            if(id==0) { throw error(HttpStatus.CONFLICT,"This event key belongs to a different submission. Open a new simulation form."); }
            return id;
        });
    }
    public boolean process(long id) {
        return transaction.execute(status -> {
            Map<String,Object> event=dao.lock(id,clock.instant()).orElse(null); if(event==null) { return false; }
            int attempts=((Number)event.get("attempts")).intValue();
            if(attempts>=3) { dao.failure(id,attempts,clock.instant()); return false; }
            dao.processing(id);
            try { nested.execute(inner -> {
                long node=((Number)event.get("node_id")).longValue(); requireNode(node,false);
                long after=0;
                while(true) {
                    List<Long> batch=dao.services(node,after); if(batch.isEmpty()) { break; }
                    for(Long service:batch) {
                        if(Thread.currentThread().isInterrupted()) { throw new IllegalStateException("Worker interrupted"); }
                        if(!dao.linked(id,service)) {
                            OptionalLong ticket=workflow.createFromNetworkEvent(service,id,"OUTAGE".equals(event.get("event_type")),(String)event.get("description"));
                            if(ticket.isPresent()) { dao.ticket(id,service,ticket.getAsLong()); }
                        }
                    }
                    after=batch.get(batch.size()-1);
                }
                dao.complete(id,clock.instant()); return null;
            }); } catch(RuntimeException failure) { dao.failure(id,attempts+1,clock.instant()); return false; }
            return true;
        });
    }
    public List<Long> pending() { return dao.pending(clock.instant()); }
    public void retry(TelecomPrincipal principal,long id) {
        transaction.execute(status -> { workflow.actor(principal,Role.SERVICE_DESK,true);
            if(dao.retry(id)!=1) { throw error(HttpStatus.CONFLICT,"Only failed or unmapped events may be retried."); } return null; });
    }
    public ResultPage<Map<String,Object>> nodes(TelecomPrincipal actor,int page) { workflow.actor(actor,Role.SERVICE_DESK,false); return new ResultPage<>(dao.nodes(offset(page)),page); }
    public ResultPage<Map<String,Object>> mappings(TelecomPrincipal actor,long node,int page) {
        workflow.actor(actor,Role.SERVICE_DESK,false); requireNode(node,false); return new ResultPage<>(dao.mappings(node,offset(page)),page);
    }
    public ResultPage<Map<String,Object>> events(TelecomPrincipal actor,int page) { workflow.actor(actor,Role.SERVICE_DESK,false); return new ResultPage<>(dao.events(offset(page)),page); }
    public Map<String,Object> event(TelecomPrincipal actor,long id) { workflow.actor(actor,Role.SERVICE_DESK,false); return dao.event(id).orElseThrow(NetworkEventService::missing); }
    public ResultPage<Map<String,Object>> tickets(TelecomPrincipal actor,long id,int page) { event(actor,id); return new ResultPage<>(dao.tickets(id,offset(page)),page); }
    private void requireNode(long id,boolean exclusive) { if(!dao.node(id,exclusive)) { throw missing(); } }
    private void validate(Object form) { if(form==null || !validator.validate(form).isEmpty()) { throw error(HttpStatus.BAD_REQUEST,"Complete all fields using the displayed limits."); } }
    private static int offset(int page) { if(page<0 || page>1000000) { throw error(HttpStatus.BAD_REQUEST,"Invalid page number."); } return page*20; }
    private static WorkflowException missing() { return error(HttpStatus.NOT_FOUND,"Network node, service or event unavailable."); }
    private static WorkflowException error(HttpStatus status,String message) { return new WorkflowException(status,message); }
}
