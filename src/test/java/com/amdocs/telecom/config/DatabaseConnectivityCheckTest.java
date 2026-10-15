package com.amdocs.telecom.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DatabaseConnectivityCheckTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseConnectivityCheck check = new DatabaseConnectivityCheck(jdbcTemplate);

    @Test
    void reportsSuccessForSelectOne(CapturedOutput output) {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        check.run(new DefaultApplicationArguments());

        assertThat(output).contains("Database connectivity check: SUCCESS");
        assertThat(output).doesNotContain("Database connectivity check: FAILURE");
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void reportsConnectionFailureWithoutLeakingDetailsOrAbortingStartup(CapturedOutput output) {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException(
                        "private-host jdbc:postgresql://internal/database password=secret-value"));

        assertThatCode(() -> check.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        assertThat(output).contains("Database connectivity check: FAILURE");
        assertThat(output).doesNotContain("Database connectivity check: SUCCESS",
                "private-host", "jdbc:postgresql", "secret-value",
                "CannotGetJdbcConnectionException", "at com.amdocs");
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void reportsFailureForUnexpectedResult(CapturedOutput output) {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(null);

        check.run(new DefaultApplicationArguments());

        assertThat(output).contains("Database connectivity check: FAILURE (unexpected verification result)");
        assertThat(output).doesNotContain("Database connectivity check: SUCCESS");
    }

    @Test
    void isDisabledWithoutDevelopmentProfile() {
        new ApplicationContextRunner()
                .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                .withUserConfiguration(DatabaseConnectivityCheck.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DatabaseConnectivityCheck.class);
                    verify(jdbcTemplate, never()).queryForObject("SELECT 1", Integer.class);
                });
    }

    @Test
    void isEnabledWithDevelopmentProfile() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=dev")
                .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                .withUserConfiguration(DatabaseConnectivityCheck.class)
                .run(context -> assertThat(context).hasSingleBean(DatabaseConnectivityCheck.class));
    }
}
