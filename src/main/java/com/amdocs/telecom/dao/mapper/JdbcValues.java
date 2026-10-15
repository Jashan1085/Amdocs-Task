package com.amdocs.telecom.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** JDBC nulls must remain null rather than becoming zero IDs or epoch timestamps. */
final class JdbcValues {

    private JdbcValues() { }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> enumType) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(enumType, value);
    }
}
