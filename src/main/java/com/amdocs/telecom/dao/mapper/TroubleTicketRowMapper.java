package com.amdocs.telecom.dao.mapper;

import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TroubleTicketRowMapper implements RowMapper<TroubleTicket> {

    @Override
    public TroubleTicket mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TroubleTicket(
                rs.getLong("ticket_id"),
                rs.getString("ticket_number"),
                rs.getLong("service_id"),
                JdbcValues.nullableLong(rs, "assigned_engineer_id"),
                JdbcValues.enumValue(rs, "category", IncidentCategory.class),
                rs.getString("description"),
                JdbcValues.enumValue(rs, "priority", TicketPriority.class),
                JdbcValues.enumValue(rs, "severity", Severity.class),
                rs.getInt("sla_policy_version"),
                JdbcValues.enumValue(rs, "status", TicketStatus.class),
                JdbcValues.instant(rs, "response_deadline"),
                JdbcValues.instant(rs, "resolution_deadline"),
                JdbcValues.instant(rs, "first_response_at"),
                JdbcValues.instant(rs, "resolved_at"),
                JdbcValues.instant(rs, "closed_at"),
                rs.getLong("row_version"),
                JdbcValues.instant(rs, "created_at"),
                JdbcValues.instant(rs, "updated_at"));
    }
}
