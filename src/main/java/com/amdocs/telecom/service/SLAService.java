package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.SLAConfigurationDAO;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.exception.WorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SLAService {
    private final SLAConfigurationDAO policies;
    private final Clock clock;
    public SLAService(SLAConfigurationDAO policies, Clock clock) { this.policies = policies; this.clock = clock; }
    public Targets targets(TicketPriority priority) {
        return targets(priority,clock.instant());
    }
    public Targets targets(TicketPriority priority,Instant created) {
        SLAConfiguration policy = policies.findLatestByPriority(priority).orElseThrow(() ->
                new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE, "The SLA policy is unavailable. Please contact the service desk."));
        return new Targets(policy, created);
    }
    public SlaEvaluation.Assessment evaluate(TroubleTicket ticket) {
        SLAConfiguration policy=policies.findByPriorityAndVersion(ticket.getPriority(),ticket.getSlaPolicyVersion())
                .orElseThrow(() -> new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE,"The ticket SLA policy is unavailable."));
        return SlaEvaluation.assess(ticket,policy,clock.instant());
    }
    public static final class Targets {
        private final SLAConfiguration policy;
        private final Instant createdAt;
        private Targets(SLAConfiguration policy, Instant createdAt) { this.policy = policy; this.createdAt = createdAt; }
        public SLAConfiguration getPolicy() { return policy; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getResponseDeadline() { return createdAt.plus(policy.getResponseMinutes(), ChronoUnit.MINUTES); }
        public Instant getResolutionDeadline() { return createdAt.plus(policy.getResolutionMinutes(), ChronoUnit.MINUTES); }
    }
}
