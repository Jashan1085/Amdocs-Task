package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.impl.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.exception.WorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;

@Service
public class EscalationService {
    private final JdbcAssignmentDAO locks;
    private final JdbcTicketWorkflowDAO intake;
    private final JdbcSlaDAO dao;
    private final SLAService slas;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public EscalationService(JdbcAssignmentDAO locks,JdbcTicketWorkflowDAO intake,JdbcSlaDAO dao,SLAService slas,Clock clock,PlatformTransactionManager manager) {
        this.locks=locks; this.intake=intake; this.dao=dao; this.slas=slas; this.clock=clock; this.transaction=new TransactionTemplate(manager);
    }
    public int inspect(long id) {
        return transaction.execute(status -> {
            dao.monitorLockTimeout();
            TroubleTicket ticket=locks.lockTicket(id).orElse(null);
            if(ticket==null || ticket.getStatus()==TicketStatus.CANCELLED || ticket.getStatus()==TicketStatus.RESOLVED || ticket.getStatus()==TicketStatus.CLOSED) { return 0; }
            JdbcSlaDAO.EscalationState state=dao.state(id,true);
            SlaEvaluation.Assessment assessment=slas.evaluate(ticket);
            int required=assessment.getRequiredLevel();
            if(required<=state.getLevel()) { return 0; }
            for(int level=state.getLevel()+1;level<=required;level++) {
                if(!dao.recipient(level)) { throw new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE,"An escalation recipient is unavailable; this check will be retried."); }
            }
            TelecomService service=intake.lockService(ticket.getServiceId()).orElseThrow(() -> new IllegalStateException("Service missing"));
            TicketStatus previous=ticket.getStatus()==TicketStatus.ESCALATED ? state.getPreviousStatus() : ticket.getStatus();
            Instant now=clock.instant();
            if(dao.status(ticket,TicketStatus.ESCALATED,now)!=1) { throw new IllegalStateException("Concurrent ticket change"); }
            if(ticket.getStatus()!=TicketStatus.ESCALATED) {
                if(!TicketTransitions.allowed(ticket.getStatus(),TicketStatus.ESCALATED)) { throw new IllegalStateException("Escalation transition unavailable"); }
                dao.statusHistory(ticket,TicketStatus.ESCALATED,"SYSTEM:SLA_MONITOR","SLA escalation",now);
            }
            dao.state(id,required,previous);
            for(int level=state.getLevel()+1;level<=required;level++) {
                long escalation=dao.escalation(id,level,previous,assessment,now);
                dao.notifications(ticket,service.getCustomerId(),escalation,level,now);
                dao.systemAudit(id,level,assessment,now);
            }
            return required-state.getLevel();
        });
    }
}
