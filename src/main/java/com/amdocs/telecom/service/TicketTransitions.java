package com.amdocs.telecom.service;

import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.Role;
import java.util.*;

/** Central graph; SLA resumption additionally requires the recorded operational status. */
public final class TicketTransitions {
    private TicketTransitions() { }
    public static boolean allowed(TicketStatus from,TicketStatus to) {
        if(from==null || to==null || from==to) { return false; }
        switch(from) {
            case OPEN: return to==TicketStatus.ASSIGNED || to==TicketStatus.CANCELLED || to==TicketStatus.ESCALATED;
            case ASSIGNED: return to==TicketStatus.IN_PROGRESS || to==TicketStatus.CANCELLED || to==TicketStatus.ESCALATED;
            case IN_PROGRESS: return to==TicketStatus.PENDING_CUSTOMER || to==TicketStatus.RESOLVED || to==TicketStatus.CANCELLED || to==TicketStatus.ESCALATED;
            case PENDING_CUSTOMER: return to==TicketStatus.IN_PROGRESS || to==TicketStatus.CANCELLED || to==TicketStatus.ESCALATED;
            case ESCALATED: return to==TicketStatus.CANCELLED || to==TicketStatus.OPEN || to==TicketStatus.ASSIGNED || to==TicketStatus.IN_PROGRESS || to==TicketStatus.PENDING_CUSTOMER;
            case RESOLVED: return to==TicketStatus.CLOSED;
            default: return false;
        }
    }
    public static boolean roleAllows(Role role,TicketAction action) {
        if(role==Role.CUSTOMER) { return action==TicketAction.CANCEL; }
        if(role==Role.SERVICE_DESK) { return action==TicketAction.CANCEL || action==TicketAction.CLOSE; }
        return role==Role.NETWORK_ENGINEER && (action==TicketAction.ACKNOWLEDGE || action==TicketAction.WAIT_CUSTOMER
                || action==TicketAction.RESUME || action==TicketAction.RESOLVE);
    }
    public static boolean canAct(Role role,TicketStatus from,TicketAction action) {
        if(action==null || !roleAllows(role,action) || !allowed(from,action.getTarget())) { return false; }
        if(from==TicketStatus.ESCALATED && action!=TicketAction.CANCEL) { return false; }
        if(role==Role.CUSTOMER && from!=TicketStatus.OPEN) { return false; }
        if(action==TicketAction.ACKNOWLEDGE) { return from==TicketStatus.ASSIGNED; }
        if(action==TicketAction.RESUME) { return from==TicketStatus.PENDING_CUSTOMER; }
        return true;
    }
    public static List<TicketAction> actions(Role role,TicketStatus from) {
        List<TicketAction> result=new ArrayList<>();
        for(TicketAction action:TicketAction.values()) { if(canAct(role,from,action)) { result.add(action); } }
        return Collections.unmodifiableList(result);
    }
}
