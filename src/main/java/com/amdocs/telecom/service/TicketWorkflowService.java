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
import java.util.OptionalLong;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TicketWorkflowService {
    private final JdbcAuthenticationDAO accounts;
    private final JdbcTicketWorkflowDAO writes;
    private final CustomerDAO customers;
    private final TelecomServiceDAO services;
    private final TroubleTicketDAO tickets;
    private final TicketStatusHistoryDAO history;
    private final SLAService slas;
    private final Clock clock;
    private final Validator validator;
    private final TransactionTemplate transaction;

    public TicketWorkflowService(JdbcAuthenticationDAO accounts, JdbcTicketWorkflowDAO writes, CustomerDAO customers,
            TelecomServiceDAO services, TroubleTicketDAO tickets, TicketStatusHistoryDAO history,
            SLAService slas, Clock clock, Validator validator, PlatformTransactionManager manager) {
        this.accounts=accounts; this.writes=writes; this.customers=customers; this.services=services;
        this.tickets=tickets; this.history=history; this.slas=slas; this.clock=clock; this.validator=validator;
        this.transaction=new TransactionTemplate(manager);
    }
    AuthAccount actor(TelecomPrincipal principal, boolean desk, boolean lock) {
        return actor(principal,desk ? Role.SERVICE_DESK : Role.CUSTOMER,lock);
    }
    AuthAccount actor(TelecomPrincipal principal, Role role, boolean lock) {
        if (principal == null) { throw forbidden(); }
        AuthAccount current = accounts.findById(principal.getUserId(), lock).orElseThrow(TicketWorkflowService::forbidden);
        if (!current.isEnabled() || current.getAuthVersion()!=principal.getAuthVersion()
                || (current.getLockedUntil()!=null && clock.instant().isBefore(current.getLockedUntil()))
                || !current.getRoles().contains(role)
                || (role==Role.CUSTOMER && current.getCustomerId()==null)
                || (role==Role.NETWORK_ENGINEER && current.getEngineerId()==null)) { throw forbidden(); }
        return current;
    }
    public Customer customer(TelecomPrincipal principal, boolean desk, String customerNumber) {
        AuthAccount current=actor(principal,desk,false);
        if (desk) { return customers.findByCustomerNumber(customerNumber == null ? "" : customerNumber.trim()).orElseThrow(TicketWorkflowService::missing); }
        return customers.findById(current.getCustomerId()).orElseThrow(TicketWorkflowService::missing);
    }
    public ResultPage<TelecomService> services(TelecomPrincipal principal, boolean desk, String customerNumber, int page) {
        Customer customer=customer(principal,desk,customerNumber);
        return new ResultPage<>(services.findByCustomerId(customer.getCustomerId(), paging(page)),page);
    }
    public TelecomService service(TelecomPrincipal principal, boolean desk, long serviceId) {
        AuthAccount current=actor(principal,desk,false);
        TelecomService service=services.findById(serviceId).orElseThrow(TicketWorkflowService::missing);
        own(current,desk,service);
        return service;
    }
    public ResultPage<TroubleTicket> tickets(TelecomPrincipal principal, boolean desk, TicketStatus status, TicketPriority priority, int page) {
        AuthAccount current=actor(principal,desk,false);
        return new ResultPage<>(writes.tickets(desk ? null : current.getCustomerId(),status,priority,paging(page)),page);
    }
    public TroubleTicket ticket(TelecomPrincipal principal, boolean desk, long ticketId) {
        AuthAccount current=actor(principal,desk,false);
        TroubleTicket ticket=tickets.findById(ticketId).orElseThrow(TicketWorkflowService::missing);
        TelecomService service=services.findById(ticket.getServiceId()).orElseThrow(TicketWorkflowService::missing);
        own(current,desk,service);
        return ticket;
    }
    public ResultPage<TicketStatusHistory> history(TelecomPrincipal principal, boolean desk, long ticketId, int page) {
        ticket(principal,desk,ticketId);
        return new ResultPage<>(history.findByTicketId(ticketId,paging(page)),page);
    }
    public long create(TelecomPrincipal principal, boolean desk, TicketCreateForm form) {
        if (form == null || !validator.validate(form).isEmpty()) {
            throw new WorkflowException(HttpStatus.BAD_REQUEST,"Complete the ticket form with a description of up to 4000 characters.");
        }
        return transaction.execute(status -> {
            AuthAccount current=actor(principal,desk,true);
            TelecomService service=writes.lockService(form.getServiceId()).orElseThrow(TicketWorkflowService::missing);
            own(current,desk,service);
            Customer owner=customers.findById(service.getCustomerId()).orElseThrow(TicketWorkflowService::missing);
            if (service.getServiceStatus()!=ServiceStatus.ACTIVE || owner.getStatus()!=CustomerStatus.ACTIVE) {
                throw new WorkflowException(HttpStatus.BAD_REQUEST,"Tickets can only be raised for an active customer and service.");
            }
            SLAService.Targets sla=slas.targets(form.getPriority());
            long id=writes.insert(form,sla);
            TroubleTicket ticket=tickets.findById(id).orElseThrow(TicketWorkflowService::missing);
            writes.initialHistory(id,current.getUserId(),sla.getCreatedAt());
            writes.notifyCustomer(id,owner.getCustomerId(),ticket.getTicketNumber(),sla.getCreatedAt());
            writes.audit(id,current.getUserId(),sla.getCreatedAt());
            return id;
        });
    }
    private static void own(AuthAccount current, boolean desk, TelecomService service) {
        if (!desk && !Long.valueOf(service.getCustomerId()).equals(current.getCustomerId())) { throw missing(); }
    }
    /** Internal event worker only; association and all ticket writes share its transaction. */
    OptionalLong createFromNetworkEvent(long serviceId,long eventId,boolean outage,String description) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Event transaction required"); }
        TelecomService service=writes.lockService(serviceId).orElseThrow(TicketWorkflowService::missing);
        Customer owner=customers.findById(service.getCustomerId()).orElseThrow(TicketWorkflowService::missing);
        if(service.getServiceStatus()!=ServiceStatus.ACTIVE || owner.getStatus()!=CustomerStatus.ACTIVE) { return OptionalLong.empty(); }
        TicketCreateForm form=new TicketCreateForm(); form.setServiceId(serviceId);
        form.setCategory(outage ? IncidentCategory.NETWORK_OUTAGE : IncidentCategory.SLOW_DATA);
        form.setPriority(outage ? TicketPriority.CRITICAL : TicketPriority.HIGH);
        form.setSeverity(outage ? Severity.CRITICAL : Severity.HIGH);
        form.setDescription("Network event "+eventId+": "+description);
        SLAService.Targets sla=slas.targets(form.getPriority());
        long id=writes.insert(form,sla); TroubleTicket ticket=tickets.findById(id).orElseThrow(TicketWorkflowService::missing);
        writes.eventHistory(id,eventId,sla.getCreatedAt());
        writes.notifyCustomer(id,owner.getCustomerId(),ticket.getTicketNumber(),sla.getCreatedAt());
        writes.eventAudit(id,eventId,sla.getCreatedAt());
        return OptionalLong.of(id);
    }
    private static PageRequest paging(int page) {
        if (page < 0 || page > 1000000) { throw new WorkflowException(HttpStatus.BAD_REQUEST,"Invalid page number."); }
        return new PageRequest(ResultPage.SIZE+1,page*ResultPage.SIZE);
    }
    private static WorkflowException missing() { return new WorkflowException(HttpStatus.NOT_FOUND,"The requested customer, service or ticket is unavailable."); }
    private static WorkflowException forbidden() { return new WorkflowException(HttpStatus.FORBIDDEN,"Your account cannot access this workflow. Please sign in again."); }
}
