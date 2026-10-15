package com.amdocs.telecom.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only development check using the application's configured datasource. */
@Component
@Profile("dev")
public class DatabaseConnectivityCheck implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConnectivityCheck.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseConnectivityCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                LOGGER.info("Database connectivity check: SUCCESS");
            } else {
                LOGGER.warn("Database connectivity check: FAILURE (unexpected verification result)");
            }
        } catch (DataAccessException exception) {
            // Do not log the exception: its message can contain connection details.
            LOGGER.warn("Database connectivity check: FAILURE (check local datasource configuration and database availability)");
        }
    }
}
