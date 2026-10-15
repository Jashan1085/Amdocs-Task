package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.TicketStatusHistory;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import com.amdocs.telecom.dao.TicketStatusHistoryDAO;
import com.amdocs.telecom.dao.mapper.TicketStatusHistoryRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JdbcTicketStatusHistoryDAO extends JdbcReadSupport<TicketStatusHistory> implements TicketStatusHistoryDAO {

    private static final String SELECT =
            "SELECT h.history_id, h.ticket_id, h.old_status, h.new_status, h.changed_by"
            + ", h.changed_at, h.remarks"
            + " FROM ticket_status_history h";

    public JdbcTicketStatusHistoryDAO(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, new TicketStatusHistoryRowMapper());
    }

    @Override
    public Optional<TicketStatusHistory> findById(long historyId) {
        return findOne(SELECT + " WHERE h.history_id = ?", historyId);
    }

    @Override
    public List<TicketStatusHistory> findByTicketId(long ticketId, PageRequest page) {
        return findPage(SELECT + " WHERE h.ticket_id = ? ORDER BY h.changed_at ASC, h.history_id ASC", page, ticketId);
    }
}
