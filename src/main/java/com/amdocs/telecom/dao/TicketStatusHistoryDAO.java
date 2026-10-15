package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.TicketStatusHistory;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import java.util.Optional;

/** Read-only persistence access; authorization belongs in the calling service. */
public interface TicketStatusHistoryDAO {

    Optional<TicketStatusHistory> findById(long historyId);

    List<TicketStatusHistory> findByTicketId(long ticketId, PageRequest page);
}
