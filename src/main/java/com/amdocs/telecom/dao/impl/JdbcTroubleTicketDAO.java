package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.TroubleTicket;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import com.amdocs.telecom.model.TicketStatus;
import com.amdocs.telecom.model.TicketPriority;
import com.amdocs.telecom.dao.TroubleTicketDAO;
import com.amdocs.telecom.dao.mapper.TroubleTicketRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.Objects;

@Repository
public class JdbcTroubleTicketDAO extends JdbcReadSupport<TroubleTicket> implements TroubleTicketDAO {

    private static final String SELECT =
            "SELECT t.ticket_id, t.ticket_number, t.service_id, t.assigned_engineer_id, t.category"
            + ", t.description, t.priority, t.severity, t.sla_policy_version, t.status"
            + ", t.response_deadline, t.resolution_deadline, t.first_response_at, t.resolved_at, t.closed_at"
            + ", t.row_version, t.created_at, t.updated_at"
            + " FROM trouble_ticket t";

    public JdbcTroubleTicketDAO(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, new TroubleTicketRowMapper());
    }

    @Override
    public Optional<TroubleTicket> findById(long ticketId) {
        return findOne(SELECT + " WHERE t.ticket_id = ?", ticketId);
    }

    @Override
    public Optional<TroubleTicket> findByTicketNumber(String ticketNumber) {
        return findOne(SELECT + " WHERE t.ticket_number = ?", ticketNumber);
    }

    @Override
    public List<TroubleTicket> findByCustomerId(long customerId, PageRequest page) {
        return findPage(SELECT + " JOIN telecom_service s ON s.service_id = t.service_id WHERE s.customer_id = ? ORDER BY t.created_at DESC, t.ticket_id DESC", page, customerId);
    }

    @Override
    public List<TroubleTicket> findByAssignedEngineerId(long engineerId, PageRequest page) {
        return findPage(SELECT + " WHERE t.assigned_engineer_id = ? ORDER BY t.created_at DESC, t.ticket_id DESC", page, engineerId);
    }

    @Override
    public List<TroubleTicket> findByStatus(TicketStatus status, PageRequest page) {
        return findPage(SELECT + " WHERE t.status = CAST(? AS ticket_status) ORDER BY t.created_at DESC, t.ticket_id DESC", page, Objects.requireNonNull(status, "status").name());
    }

    @Override
    public List<TroubleTicket> findByPriority(TicketPriority priority, PageRequest page) {
        return findPage(SELECT + " WHERE t.priority = CAST(? AS ticket_priority) ORDER BY t.created_at DESC, t.ticket_id DESC", page, Objects.requireNonNull(priority, "priority").name());
    }
}
