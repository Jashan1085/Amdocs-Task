package com.amdocs.telecom.dao.mapper;

import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TicketStatusHistoryRowMapper implements RowMapper<TicketStatusHistory> {

    @Override
    public TicketStatusHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TicketStatusHistory(
                rs.getLong("history_id"),
                rs.getLong("ticket_id"),
                JdbcValues.enumValue(rs, "old_status", TicketStatus.class),
                JdbcValues.enumValue(rs, "new_status", TicketStatus.class),
                rs.getString("changed_by"),
                JdbcValues.instant(rs, "changed_at"),
                rs.getString("remarks"));
    }
}
