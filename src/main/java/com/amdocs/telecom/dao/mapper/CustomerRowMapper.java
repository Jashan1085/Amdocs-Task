package com.amdocs.telecom.dao.mapper;

import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class CustomerRowMapper implements RowMapper<Customer> {

    @Override
    public Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Customer(
                rs.getLong("customer_id"),
                rs.getString("customer_number"),
                rs.getString("customer_name"),
                rs.getString("email"),
                rs.getString("mobile_number"),
                JdbcValues.enumValue(rs, "customer_type", CustomerType.class),
                rs.getString("city"),
                JdbcValues.enumValue(rs, "status", CustomerStatus.class),
                JdbcValues.instant(rs, "created_at"),
                JdbcValues.instant(rs, "updated_at"));
    }
}
