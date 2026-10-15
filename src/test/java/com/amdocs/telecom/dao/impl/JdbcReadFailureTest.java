package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcReadFailureTest {

    @Test
    void aConnectionFailureIsNotMisreportedAsAMissingCustomer() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Customer>>any(), eq(123L)))
                .thenThrow(new CannotGetJdbcConnectionException("test connection unavailable"));

        assertThatThrownBy(() -> new JdbcCustomerDAO(jdbcTemplate).findById(123L))
                .isInstanceOf(CannotGetJdbcConnectionException.class);
    }
}
