package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.exception.WorkflowException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.*;
import java.util.*;

@Service
public class SlaOperationsService {
    private final TicketWorkflowService workflow;
    private final JdbcAssignmentDAO locks;
    private final JdbcTicketWorkflowDAO intake;
    private final JdbcSlaDAO dao;
    private final JdbcLifecycleDAO events;
    private final SLAService slas;
    private final Validator validator;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public SlaOperationsService(TicketWorkflowService workflow,JdbcAssignmentDAO locks,JdbcTicketWorkflowDAO intake,JdbcSlaDAO dao,
            JdbcLifecycleDAO events,SLAService slas,Validator validator,Clock clock,PlatformTransactionManager manager) {
        this.workflow=workflow; this.locks=locks; this.intake=intake; this.dao=dao; this.events=events; this.slas=slas;
        this.validator=validator; this.clock=clock; this.transaction=new TransactionTemplate(manager);
    }
    public static boolean editable(TroubleTicket ticket) {
        return ticket.getStatus()!=TicketStatus.RESOLVED && ticket.getStatus()!=TicketStatus.CLOSED && ticket.getStatus()!=TicketStatus.CANCELLED;
    }
    public ResultPage<Map<String,Object>> list(TelecomPrincipal actor,Role role,TicketStatus status,TicketPriority priority,int page) {
        requireViewer(actor,role);
        if(page<0 || page>1000000) { throw bad("Invalid page number."); }
        List<Map<String,Object>> rows=new ArrayList<>();
        for(TroubleTicket ticket:intake.tickets(null,status,priority,new PageRequest(21,page*20))) {
            Map<String,Object> row=new HashMap<>(); row.put("ticket",ticket); row.put("sla",slas.evaluate(ticket));
            row.put("escalation",dao.state(ticket.getTicketId(),false)); rows.add(row);
        }
        return new ResultPage<>(rows,page);
    }
    public Map<String,Object> counters(TelecomPrincipal actor,Role role) { requireViewer(actor,role); return dao.counters(clock.instant()); }
    private void requireViewer(TelecomPrincipal actor,Role role) {
        if(role!=Role.SERVICE_DESK && role!=Role.NETWORK_MANAGER) { throw new WorkflowException(HttpStatus.FORBIDDEN,"SLA operations are restricted to desk and manager roles."); }
        workflow.actor(actor,role,false);
    }
    public void priority(TelecomPrincipal principal,long id,SlaChangeForm form) {
        validate(form);
        if(form.getPriority()==null) { throw bad("Choose a priority."); }
        transaction.execute(status -> {
            AuthAccount actor=workflow.actor(principal,true,true);
            TroubleTicket ticket=locks.lockTicket(id).orElseThrow(SlaOperationsService::missing);
            version(ticket,form.getRowVersion());
            if(!editable(ticket)) { throw new WorkflowException(HttpStatus.CONFLICT,"Priority cannot change after resolution, closure or cancellation."); }
            if(ticket.getPriority()==form.getPriority()) { throw bad("Choose a different priority."); }
            TelecomService service=intake.lockService(ticket.getServiceId()).orElseThrow(SlaOperationsService::missing);
            SLAService.Targets targets=slas.targets(form.getPriority(),ticket.getCreatedAt());
            Instant now=clock.instant();
            if(dao.priority(ticket,form.getPriority(),targets,now)!=1) { throw conflict(); }
            dao.priorityAudit(ticket,form.getPriority(),targets.getPolicy().getPolicyVersion(),actor.getUserId(),form.getReason().trim(),now);
            events.notifications(ticket,service.getCustomerId(),"PRIORITY_CHANGED","Ticket "+ticket.getTicketNumber()+" priority changed to "+form.getPriority()+".",now);
            return null;
        });
    }
    public void resume(TelecomPrincipal principal,long id,SlaChangeForm form) {
        validate(form);
        transaction.execute(status -> {
            AuthAccount actor=workflow.actor(principal,true,true);
            TroubleTicket ticket=locks.lockTicket(id).orElseThrow(SlaOperationsService::missing);
            version(ticket,form.getRowVersion());
            JdbcSlaDAO.EscalationState state=dao.state(id,true);
            if(ticket.getStatus()!=TicketStatus.ESCALATED || state.getPreviousStatus()==null
                    || !TicketTransitions.allowed(TicketStatus.ESCALATED,state.getPreviousStatus())) {
                throw new WorkflowException(HttpStatus.CONFLICT,"No recorded operational status is available for resumption.");
            }
            TelecomService service=intake.lockService(ticket.getServiceId()).orElseThrow(SlaOperationsService::missing);
            Instant now=clock.instant();
            if(dao.status(ticket,state.getPreviousStatus(),now)!=1) { throw conflict(); }
            dao.clearPrevious(id);
            dao.statusHistory(ticket,state.getPreviousStatus(),"user:"+actor.getUserId(),form.getReason().trim(),now);
            events.notifications(ticket,service.getCustomerId(),"STATUS_CHANGED","Ticket "+ticket.getTicketNumber()+" resumed as "+state.getPreviousStatus()+".",now);
            events.audit(id,actor.getUserId(),"ESCALATION_RESUMED",form.getReason().trim(),now);
            return null;
        });
    }
    private void validate(SlaChangeForm form) { if(form==null || !validator.validate(form).isEmpty()) { throw bad("A current ticket version and reason (up to 1000 characters) are required."); } }
    private static void version(TroubleTicket ticket,long version) { if(ticket.getRowVersion()!=version) { throw conflict(); } }
    private static WorkflowException missing() { return new WorkflowException(HttpStatus.NOT_FOUND,"Ticket unavailable."); }
    private static WorkflowException bad(String reason) { return new WorkflowException(HttpStatus.BAD_REQUEST,reason); }
    private static WorkflowException conflict() { return new WorkflowException(HttpStatus.CONFLICT,"This ticket changed. Reload it before saving."); }
}
