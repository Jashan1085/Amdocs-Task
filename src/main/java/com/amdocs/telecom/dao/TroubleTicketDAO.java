package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.TroubleTicket;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import com.amdocs.telecom.model.TicketStatus;
import com.amdocs.telecom.model.TicketPriority;
import java.util.Optional;

/** Read-only persistence access; authorization belongs in the calling service. */
public interface TroubleTicketDAO {

    Optional<TroubleTicket> findById(long ticketId);

    Optional<TroubleTicket> findByTicketNumber(String ticketNumber);

    List<TroubleTicket> findByCustomerId(long customerId, PageRequest page);

    List<TroubleTicket> findByAssignedEngineerId(long engineerId, PageRequest page);

    List<TroubleTicket> findByStatus(TicketStatus status, PageRequest page);

    List<TroubleTicket> findByPriority(TicketPriority priority, PageRequest page);
}
