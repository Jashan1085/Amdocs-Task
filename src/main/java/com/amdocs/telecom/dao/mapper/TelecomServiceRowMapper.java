package com.amdocs.telecom.dao.mapper;

import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TelecomServiceRowMapper implements RowMapper<TelecomService> {

    @Override
    public TelecomService mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TelecomService(
                rs.getLong("service_id"),
                rs.getLong("customer_id"),
                rs.getString("service_code"),
                rs.getString("service_name"),
                JdbcValues.enumValue(rs, "service_type", ServiceType.class),
                rs.getString("region"),
                rs.getObject("activation_date", java.time.LocalDate.class),
                JdbcValues.enumValue(rs, "service_status", ServiceStatus.class),
                JdbcValues.instant(rs, "created_at"),
                JdbcValues.instant(rs, "updated_at"));
    }
}
