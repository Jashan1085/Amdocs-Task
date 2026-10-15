package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.*;
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
import java.time.Clock;
import java.time.Instant;

@Service
public class LifecycleService {
    private final TicketWorkflowService workflow;
    private final JdbcLifecycleDAO dao;
    private final JdbcAssignmentDAO locks;
    private final JdbcTicketWorkflowDAO intake;
    private final TroubleTicketDAO tickets;
    private final TelecomServiceDAO services;
    private final Clock clock;
    private final Validator validator;
    private final TransactionTemplate transaction;
    public LifecycleService(TicketWorkflowService workflow,JdbcLifecycleDAO dao,JdbcAssignmentDAO locks,JdbcTicketWorkflowDAO intake,
            TroubleTicketDAO tickets,TelecomServiceDAO services,Clock clock,Validator validator,PlatformTransactionManager manager) {
        this.workflow=workflow; this.dao=dao; this.locks=locks; this.intake=intake; this.tickets=tickets; this.services=services;
        this.clock=clock; this.validator=validator; this.transaction=new TransactionTemplate(manager);
    }
    public TroubleTicket read(TelecomPrincipal principal,Role role,long id) {
        AuthAccount actor=workflow.actor(principal,role,false);
        TroubleTicket ticket=tickets.findById(id).orElseThrow(LifecycleService::missing);
        TelecomService service=services.findById(ticket.getServiceId()).orElseThrow(LifecycleService::missing);
        authorize(actor,role,ticket,service,false);
        return ticket;
    }
    public ResultPage<TroubleTicket> engineerTickets(TelecomPrincipal principal,TicketStatus status,TicketPriority priority,int page) {
        AuthAccount actor=workflow.actor(principal,Role.NETWORK_ENGINEER,false);
        if(page<0 || page>1000000) { throw bad("Invalid page number."); }
        return new ResultPage<>(intake.engineerTickets(actor.getEngineerId(),status,priority,new PageRequest(21,page*20)),page);
    }
    private void authorize(AuthAccount actor,Role role,TroubleTicket ticket,TelecomService service,boolean locked) {
        if(role==Role.SERVICE_DESK) { return; }
        if(role==Role.CUSTOMER && Long.valueOf(service.getCustomerId()).equals(actor.getCustomerId())
                && (!locked || dao.linkedCustomer(service.getCustomerId(),actor.getUserId()))) { return; }
        if(role==Role.NETWORK_ENGINEER && actor.getEngineerId().equals(ticket.getAssignedEngineerId())
                && (!locked || dao.linkedEngineer(actor.getEngineerId(),actor.getUserId()))) { return; }
        throw missing();
    }
    public void requireAction(Role role,TroubleTicket ticket,TicketAction action) {
        if(action==null || !TicketTransitions.roleAllows(role,action)) { throw new WorkflowException(HttpStatus.FORBIDDEN,"Your role cannot perform this action."); }
        if(!TicketTransitions.canAct(role,ticket.getStatus(),action)) { throw new WorkflowException(HttpStatus.CONFLICT,"This action is unavailable for the current ticket status. Reload the ticket."); }
        if(action==TicketAction.CLOSE && dao.resolution(ticket.getTicketId())==null) {
            throw new WorkflowException(HttpStatus.CONFLICT,"Resolution details are missing. This legacy ticket requires reviewed resolution data before closure.");
        }
    }
    public void act(TelecomPrincipal principal,Role role,long id,LifecycleForm form) {
        if(form==null || !validator.validate(form).isEmpty()) { throw bad("Check the action, version and field lengths."); }
        transaction.execute(status -> {
            AuthAccount actor=workflow.actor(principal,role,true);
            TroubleTicket ticket=locks.lockTicket(id).orElseThrow(LifecycleService::missing);
            TelecomService service=intake.lockService(ticket.getServiceId()).orElseThrow(LifecycleService::missing);
            authorize(actor,role,ticket,service,true);
            version(ticket,form.getRowVersion()); requireAction(role,ticket,form.getAction());
            TicketAction action=form.getAction();
            String reason=form.getReason()==null ? null : form.getReason().trim();
            if((action==TicketAction.CANCEL || action==TicketAction.WAIT_CUSTOMER) && blank(reason)) { throw bad("A reason is required for cancellation or a customer-information request."); }
            if(action==TicketAction.RESOLVE && (blank(form.getRootCause()) || blank(form.getResolutionText()) || form.getResolutionCode()==null)) {
                throw bad("Root cause, resolution details and resolution code are required.");
            }
            Instant now=clock.instant();
            if(dao.change(ticket,action,now)!=1) { throw conflict(); }
            if(action==TicketAction.RESOLVE) { dao.resolution(id,actor.getEngineerId(),form,now); }
            if(action==TicketAction.CANCEL) { dao.cancellation(id,actor.getUserId(),reason,now); }
            dao.history(ticket,action,actor.getUserId(),reason,now);
            dao.notifications(ticket,service.getCustomerId(),"STATUS_CHANGED","Ticket "+ticket.getTicketNumber()+" is now "+action.getTarget().name()+".",now);
            dao.audit(id,actor.getUserId(),"TICKET_"+action.name(),reason,now);
            return null;
        });
    }
    public void feedback(TelecomPrincipal principal,long id,FeedbackForm form) {
        if(form==null || !validator.validate(form).isEmpty()) { throw bad("Choose a rating from 1 to 5 and comments of at most 2000 characters."); }
        transaction.execute(status -> {
            AuthAccount actor=workflow.actor(principal,Role.CUSTOMER,true);
            TroubleTicket ticket=locks.lockTicket(id).orElseThrow(LifecycleService::missing);
            TelecomService service=intake.lockService(ticket.getServiceId()).orElseThrow(LifecycleService::missing);
            authorize(actor,Role.CUSTOMER,ticket,service,true); version(ticket,form.getRowVersion());
            requireFeedback(ticket);
            Instant now=clock.instant();
            if(dao.touch(ticket,now)!=1) { throw conflict(); }
            dao.feedback(id,service.getCustomerId(),form,now);
            dao.notifications(ticket,service.getCustomerId(),"FEEDBACK_RECEIVED","Feedback recorded for ticket "+ticket.getTicketNumber()+".",now);
            dao.audit(id,actor.getUserId(),"FEEDBACK_RECEIVED","Rating: "+form.getRating(),now);
            return null;
        });
    }
    public void requireFeedback(TroubleTicket ticket) {
        if(ticket.getStatus()!=TicketStatus.CLOSED || dao.feedback(ticket.getTicketId())!=null) {
            throw new WorkflowException(HttpStatus.CONFLICT,"Feedback is available once after ticket closure.");
        }
    }
    private static boolean blank(String value) { return value==null || value.trim().isEmpty(); }
    private static void version(TroubleTicket ticket,long value) { if(ticket.getRowVersion()!=value) { throw conflict(); } }
    private static WorkflowException bad(String text) { return new WorkflowException(HttpStatus.BAD_REQUEST,text); }
    private static WorkflowException missing() { return new WorkflowException(HttpStatus.NOT_FOUND,"Ticket unavailable for this account."); }
    private static WorkflowException conflict() { return new WorkflowException(HttpStatus.CONFLICT,"This ticket changed while you were editing it. Reload the ticket and try again."); }
}
