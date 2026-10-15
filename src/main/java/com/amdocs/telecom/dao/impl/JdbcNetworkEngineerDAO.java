package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.NetworkEngineer;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import com.amdocs.telecom.dao.NetworkEngineerDAO;
import com.amdocs.telecom.dao.mapper.NetworkEngineerRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JdbcNetworkEngineerDAO extends JdbcReadSupport<NetworkEngineer> implements NetworkEngineerDAO {

    private static final String SELECT =
            "SELECT e.engineer_id, e.employee_code, e.engineer_name, e.specialization, e.region"
            + ", e.experience_years, e.available, e.created_at, e.updated_at"
            + " FROM network_engineer e";

    public JdbcNetworkEngineerDAO(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, new NetworkEngineerRowMapper());
    }

    @Override
    public Optional<NetworkEngineer> findById(long engineerId) {
        return findOne(SELECT + " WHERE e.engineer_id = ?", engineerId);
    }

    @Override
    public Optional<NetworkEngineer> findByEmployeeCode(String employeeCode) {
        return findOne(SELECT + " WHERE e.employee_code = ?", employeeCode);
    }

    @Override
    public List<NetworkEngineer> findAvailableBySpecializationAndRegion(String specialization, String region, PageRequest page) {
        return findPage(SELECT + " WHERE e.available = TRUE AND e.specialization = ? AND e.region = ? ORDER BY e.employee_code ASC", page, specialization, region);
    }
}
