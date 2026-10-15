package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dto.PageRequest;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQL is supplied only by concrete DAOs, never by callers or web inputs. */
abstract class JdbcReadSupport<T> {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<T> rowMapper;

    JdbcReadSupport(JdbcTemplate jdbcTemplate, RowMapper<T> rowMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.rowMapper = rowMapper;
    }

    protected Optional<T> findOne(String sql, Object... arguments) {
        return Optional.ofNullable(DataAccessUtils.singleResult(jdbcTemplate.query(sql, rowMapper, arguments)));
    }

    protected List<T> findPage(String sql, PageRequest page, Object... arguments) {
        Objects.requireNonNull(page, "page");
        Object[] parameters = Arrays.copyOf(arguments, arguments.length + 2);
        parameters[arguments.length] = page.getLimit();
        parameters[arguments.length + 1] = page.getOffset();
        return jdbcTemplate.query(sql + " LIMIT ? OFFSET ?", rowMapper, parameters);
    }
}
