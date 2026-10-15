package com.amdocs.telecom.dao.mapper;

import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class NetworkEngineerRowMapper implements RowMapper<NetworkEngineer> {

    @Override
    public NetworkEngineer mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NetworkEngineer(
                rs.getLong("engineer_id"),
                rs.getString("employee_code"),
                rs.getString("engineer_name"),
                rs.getString("specialization"),
                rs.getString("region"),
                rs.getInt("experience_years"),
                rs.getBoolean("available"),
                JdbcValues.instant(rs, "created_at"),
                JdbcValues.instant(rs, "updated_at"));
    }
}
