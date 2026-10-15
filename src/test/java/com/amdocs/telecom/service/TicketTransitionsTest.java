package com.amdocs.telecom.service;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class TicketTransitionsTest {
    @ParameterizedTest @EnumSource(TicketStatus.class)
    void enforcesManualGraphIncludingTerminalAndSameStateRejection(TicketStatus from) {
        Set<String> permitted=new HashSet<>(Arrays.asList("OPEN:ASSIGNED","OPEN:CANCELLED","ASSIGNED:IN_PROGRESS","ASSIGNED:CANCELLED",
                "IN_PROGRESS:PENDING_CUSTOMER","IN_PROGRESS:RESOLVED","IN_PROGRESS:CANCELLED","PENDING_CUSTOMER:IN_PROGRESS",
                "PENDING_CUSTOMER:CANCELLED","ESCALATED:CANCELLED","RESOLVED:CLOSED",
                "OPEN:ESCALATED","ASSIGNED:ESCALATED","IN_PROGRESS:ESCALATED","PENDING_CUSTOMER:ESCALATED",
                "ESCALATED:OPEN","ESCALATED:ASSIGNED","ESCALATED:IN_PROGRESS","ESCALATED:PENDING_CUSTOMER"));
        for(TicketStatus to:TicketStatus.values()) { assertThat(TicketTransitions.allowed(from,to)).isEqualTo(permitted.contains(from+":"+to)); }
    }
    @Test void roleAndActionAreMoreRestrictiveThanTheGraph() {
        assertThat(TicketTransitions.canAct(Role.CUSTOMER,TicketStatus.OPEN,TicketAction.CANCEL)).isTrue();
        assertThat(TicketTransitions.canAct(Role.CUSTOMER,TicketStatus.ASSIGNED,TicketAction.CANCEL)).isFalse();
        assertThat(TicketTransitions.canAct(Role.SERVICE_DESK,TicketStatus.ESCALATED,TicketAction.CANCEL)).isTrue();
        assertThat(TicketTransitions.canAct(Role.NETWORK_ENGINEER,TicketStatus.PENDING_CUSTOMER,TicketAction.ACKNOWLEDGE)).isFalse();
        assertThat(TicketTransitions.canAct(Role.NETWORK_ENGINEER,TicketStatus.ASSIGNED,TicketAction.RESUME)).isFalse();
        assertThat(TicketTransitions.actions(Role.NETWORK_MANAGER,TicketStatus.OPEN)).isEmpty();
    }
}
