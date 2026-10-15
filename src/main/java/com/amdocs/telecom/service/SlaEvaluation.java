package com.amdocs.telecom.service;

import com.amdocs.telecom.model.*;
import java.time.*;

/** Pure boundary rules: warning is inclusive, unfinished work breaches at deadline. */
public final class SlaEvaluation {
    private SlaEvaluation() { }
    public enum State { ON_TRACK, AT_RISK, BREACHED, MET, NOT_APPLICABLE }
    public static Metric metric(Instant created,Instant deadline,Instant completed,Instant now,int warningPercent,boolean cancelled) {
        Instant warning=created.plus(Duration.between(created,deadline).multipliedBy(warningPercent).dividedBy(100));
        State state=cancelled ? State.NOT_APPLICABLE : (completed!=null ? (completed.isAfter(deadline) ? State.BREACHED : State.MET)
                : (!now.isBefore(deadline) ? State.BREACHED : (!now.isBefore(warning) ? State.AT_RISK : State.ON_TRACK)));
        return new Metric(state,completed!=null,warning,deadline,Duration.between(completed==null ? now : completed,deadline).getSeconds());
    }
    public static Assessment assess(TroubleTicket ticket,SLAConfiguration policy,Instant now) {
        boolean cancelled=ticket.getStatus()==TicketStatus.CANCELLED;
        Metric response=metric(ticket.getCreatedAt(),ticket.getResponseDeadline(),ticket.getFirstResponseAt(),now,policy.getWarningPercent(),cancelled);
        Metric resolution=metric(ticket.getCreatedAt(),ticket.getResolutionDeadline(),ticket.getResolvedAt(),now,policy.getWarningPercent(),cancelled);
        boolean active=ticket.getStatus()!=TicketStatus.RESOLVED && ticket.getStatus()!=TicketStatus.CLOSED && !cancelled;
        int level=0;
        if(active) {
            Instant severe=ticket.getResolutionDeadline().plus(Duration.between(ticket.getCreatedAt(),ticket.getResolutionDeadline()));
            if(!resolution.isCompleted() && !now.isBefore(severe)) { level=3; }
            else if(response.active(State.BREACHED) || resolution.active(State.BREACHED)) { level=2; }
            else if(response.active(State.AT_RISK) || resolution.active(State.AT_RISK)) { level=1; }
        }
        return new Assessment(response,resolution,level);
    }
    public static final class Metric {
        private final State state;
        private final boolean completed;
        private final Instant warningAt,deadline;
        private final long remainingSeconds;
        private Metric(State state,boolean completed,Instant warningAt,Instant deadline,long remainingSeconds) { this.state=state; this.completed=completed; this.warningAt=warningAt; this.deadline=deadline; this.remainingSeconds=remainingSeconds; }
        public long getRemainingSeconds() { return remainingSeconds; }
        public State getState() { return state; }
        public boolean isCompleted() { return completed; }
        public Instant getWarningAt() { return warningAt; }
        public Instant getDeadline() { return deadline; }
        public boolean active(State value) { return !completed && state==value; }
    }
    public static final class Assessment {
        private final Metric response,resolution;
        private final int requiredLevel;
        private Assessment(Metric response,Metric resolution,int requiredLevel) { this.response=response; this.resolution=resolution; this.requiredLevel=requiredLevel; }
        public Metric getResponse() { return response; }
        public Metric getResolution() { return resolution; }
        public int getRequiredLevel() { return requiredLevel; }
    }
}
