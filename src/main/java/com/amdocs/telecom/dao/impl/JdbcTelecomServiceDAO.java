package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.TelecomService;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import com.amdocs.telecom.dao.TelecomServiceDAO;
import com.amdocs.telecom.dao.mapper.TelecomServiceRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JdbcTelecomServiceDAO extends JdbcReadSupport<TelecomService> implements TelecomServiceDAO {

    private static final String SELECT =
            "SELECT s.service_id, s.customer_id, s.service_code, s.service_name, s.service_type"
            + ", s.region, s.activation_date, s.service_status, s.created_at, s.updated_at"
            + " FROM telecom_service s";

    public JdbcTelecomServiceDAO(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, new TelecomServiceRowMapper());
    }

    @Override
    public Optional<TelecomService> findById(long serviceId) {
        return findOne(SELECT + " WHERE s.service_id = ?", serviceId);
    }

    @Override
    public Optional<TelecomService> findByServiceCode(String serviceCode) {
        return findOne(SELECT + " WHERE s.service_code = ?", serviceCode);
    }

    @Override
    public List<TelecomService> findByCustomerId(long customerId, PageRequest page) {
        return findPage(SELECT + " WHERE s.customer_id = ? ORDER BY s.service_id ASC", page, customerId);
    }
}
