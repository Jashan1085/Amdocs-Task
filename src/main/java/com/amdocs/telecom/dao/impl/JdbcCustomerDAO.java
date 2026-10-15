package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.dao.CustomerDAO;
import com.amdocs.telecom.dao.mapper.CustomerRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JdbcCustomerDAO extends JdbcReadSupport<Customer> implements CustomerDAO {

    private static final String SELECT =
            "SELECT c.customer_id, c.customer_number, c.customer_name, c.email, c.mobile_number"
            + ", c.customer_type, c.city, c.status, c.created_at, c.updated_at"
            + " FROM customer c";

    public JdbcCustomerDAO(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, new CustomerRowMapper());
    }

    @Override
    public Optional<Customer> findById(long customerId) {
        return findOne(SELECT + " WHERE c.customer_id = ?", customerId);
    }

    @Override
    public Optional<Customer> findByCustomerNumber(String customerNumber) {
        return findOne(SELECT + " WHERE c.customer_number = ?", customerNumber);
    }
}
