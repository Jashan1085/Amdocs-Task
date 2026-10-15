package com.amdocs.telecom.dao.mapper;

import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class SLAConfigurationRowMapper implements RowMapper<SLAConfiguration> {

    @Override
    public SLAConfiguration mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SLAConfiguration(
                JdbcValues.enumValue(rs, "priority", TicketPriority.class),
                rs.getInt("policy_version"),
                rs.getInt("response_minutes"),
                rs.getInt("resolution_minutes"),
                rs.getInt("warning_percent"),
                JdbcValues.instant(rs, "created_at"));
    }
}
