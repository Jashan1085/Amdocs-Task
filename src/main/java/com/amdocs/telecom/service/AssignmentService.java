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
import java.util.*;

@Service
public class AssignmentService {
    private final TicketWorkflowService workflow;
    private final JdbcAssignmentDAO dao;
    private final JdbcTicketWorkflowDAO intake;
    private final NetworkEngineerDAO engineers;
    private final Clock clock;
    private final Validator validator;
    private final TransactionTemplate transaction;
    public AssignmentService(TicketWorkflowService workflow,JdbcAssignmentDAO dao,JdbcTicketWorkflowDAO intake,
            NetworkEngineerDAO engineers,Clock clock,Validator validator,PlatformTransactionManager manager) {
        this.workflow=workflow; this.dao=dao; this.intake=intake; this.engineers=engineers;
        this.clock=clock; this.validator=validator; this.transaction=new TransactionTemplate(manager);
    }
    public static boolean isAssignable(TicketStatus status) {
        return status==TicketStatus.OPEN || status==TicketStatus.ASSIGNED || status==TicketStatus.IN_PROGRESS || status==TicketStatus.PENDING_CUSTOMER;
    }
    private static void requireAssignable(TroubleTicket ticket) {
        if(!isAssignable(ticket.getStatus())) { throw new WorkflowException(HttpStatus.CONFLICT,"Assignment is unavailable for this ticket status."); }
    }
    public Options options(TelecomPrincipal actor,long id) {
        TroubleTicket ticket=workflow.ticket(actor,true,id);
        requireAssignable(ticket);
        TelecomService service=workflow.service(actor,true,ticket.getServiceId());
        Optional<String> skill=dao.specialization(ticket.getCategory(),false);
        List<EngineerRecommendation> recommended=skill.map(value -> EngineerRanking.topThree(
                dao.recommendations(value,service.getRegion(),ticket.getAssignedEngineerId()))).orElse(Collections.emptyList());
        NetworkEngineer current=ticket.getAssignedEngineerId()==null ? null : engineers.findById(ticket.getAssignedEngineerId()).orElse(null);
        return new Options(ticket,service,skill.orElse(null),recommended,current);
    }
    public void assign(TelecomPrincipal actor,long id,AssignmentForm form) {
        if(form==null || !validator.validate(form).isEmpty()) {
            throw new WorkflowException(HttpStatus.BAD_REQUEST,"Choose an engineer and reload the ticket if its version is missing.");
        }
        transaction.execute(status -> {
            AuthAccount current=workflow.actor(actor,true,true);
            TroubleTicket ticket=dao.lockTicket(id).orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,"Ticket unavailable."));
            if(ticket.getRowVersion()!=form.getRowVersion()) { throw conflict(); }
            requireAssignable(ticket);
            boolean first=ticket.getStatus()==TicketStatus.OPEN;
            if(first && !TicketTransitions.allowed(ticket.getStatus(),TicketStatus.ASSIGNED)) { throw conflict(); }
            if(Objects.equals(ticket.getAssignedEngineerId(),form.getEngineerId())) {
                throw new WorkflowException(HttpStatus.BAD_REQUEST,"Choose a different engineer for reassignment.");
            }
            String reason=form.getReason()==null ? null : form.getReason().trim();
            if(!first && (reason==null || reason.isEmpty())) {
                throw new WorkflowException(HttpStatus.BAD_REQUEST,"Enter a reason for reassignment.");
            }
            TelecomService service=intake.lockService(ticket.getServiceId()).orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,"Service unavailable."));
            String skill=dao.specialization(ticket.getCategory(),true).orElseThrow(() ->
                    new WorkflowException(HttpStatus.CONFLICT,"No engineer specialization is configured for this category."));
            NetworkEngineer engineer=dao.lockEngineers(form.getEngineerId(),ticket.getAssignedEngineerId()).stream()
                    .filter(value -> value.getEngineerId()==form.getEngineerId()).findFirst().orElseThrow(() ->
                            new WorkflowException(HttpStatus.CONFLICT,"The selected engineer is no longer eligible. Refresh recommendations."));
            if(!engineer.isAvailable() || !skill.equals(engineer.getSpecialization()) || !service.getRegion().equals(engineer.getRegion())) {
                throw new WorkflowException(HttpStatus.CONFLICT,"The selected engineer is no longer eligible. Refresh recommendations.");
            }
            Instant time=clock.instant();
            if(dao.assign(ticket,engineer.getEngineerId(),first ? TicketStatus.ASSIGNED : ticket.getStatus(),time)!=1) { throw conflict(); }
            if(first) { dao.statusHistory(id,current.getUserId(),time); }
            dao.notifications(id,service.getCustomerId(),engineer.getEngineerId(),ticket.getTicketNumber(),time);
            dao.audit(ticket,engineer.getEngineerId(),current.getUserId(),reason,time);
            return null;
        });
    }
    private static WorkflowException conflict() { return new WorkflowException(HttpStatus.CONFLICT,"This ticket changed while you were editing it. Reload the ticket and try again."); }
    public static final class Options {
        private final TroubleTicket ticket;
        private final TelecomService service;
        private final String specialization;
        private final List<EngineerRecommendation> recommendations;
        private final NetworkEngineer currentEngineer;
        private Options(TroubleTicket ticket,TelecomService service,String specialization,List<EngineerRecommendation> recommendations,NetworkEngineer currentEngineer) {
            this.ticket=ticket; this.service=service; this.specialization=specialization;
            this.recommendations=recommendations; this.currentEngineer=currentEngineer;
        }
        public TroubleTicket getTicket() { return ticket; }
        public TelecomService getService() { return service; }
        public String getSpecialization() { return specialization; }
        public List<EngineerRecommendation> getRecommendations() { return recommendations; }
        public NetworkEngineer getCurrentEngineer() { return currentEngineer; }
    }
}
