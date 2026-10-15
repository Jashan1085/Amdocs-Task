package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.model.SLAConfiguration;
import com.amdocs.telecom.model.TicketPriority;
import com.amdocs.telecom.dao.SLAConfigurationDAO;
import com.amdocs.telecom.dao.mapper.SLAConfigurationRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.Objects;

@Repository
public class JdbcSLAConfigurationDAO extends JdbcReadSupport<SLAConfiguration> implements SLAConfigurationDAO {

    private static final String SELECT =
            "SELECT p.priority, p.policy_version, p.response_minutes, p.resolution_minutes, p.warning_percent"
            + ", p.created_at"
            + " FROM sla_configuration p";

    public JdbcSLAConfigurationDAO(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate, new SLAConfigurationRowMapper());
    }

    @Override
    public Optional<SLAConfiguration> findByPriorityAndVersion(TicketPriority priority, int policyVersion) {
        return findOne(SELECT + " WHERE p.priority = CAST(? AS ticket_priority) AND p.policy_version = ?", Objects.requireNonNull(priority, "priority").name(), policyVersion);
    }

    @Override
    public Optional<SLAConfiguration> findLatestByPriority(TicketPriority priority) {
        return findOne(SELECT + " WHERE p.priority = CAST(? AS ticket_priority) ORDER BY p.policy_version DESC LIMIT 1", Objects.requireNonNull(priority, "priority").name());
    }
}
