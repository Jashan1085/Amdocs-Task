package com.amdocs.telecom.tool;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseSetupTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final PreparedStatement record = mock(PreparedStatement.class);
    private final ResultSet objects = mock(ResultSet.class);

    private void configure(boolean occupied) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(objects);
        when(objects.getBoolean(1)).thenReturn(occupied);
        when(connection.prepareStatement(anyString())).thenReturn(record);
    }

    @Test
    void refusesAnOccupiedSchemaWithoutCommitting() throws Exception {
        configure(true);

        assertThatThrownBy(() -> DatabaseSetup.install(dataSource, true))
                .isInstanceOf(IllegalStateException.class);

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(statement, never()).execute(contains("CREATE TABLE customer"));
    }

    @Test
    void rollsBackSchemaWhenSeedingFails() throws Exception {
        configure(false);
        when(statement.execute(contains("INSERT INTO customer"))).thenThrow(new SQLException("seed failure"));

        assertThatThrownBy(() -> DatabaseSetup.install(dataSource, true)).isInstanceOf(SQLException.class);

        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void commitsBaselineAndLedgerWithoutOptionalFixtures() throws Exception {
        configure(false);

        DatabaseSetup.install(dataSource, false);

        verify(connection).setAutoCommit(false);
        verify(record).setBoolean(3, false);
        verify(record).executeUpdate();
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(statement, never()).execute(contains("INSERT INTO customer"));
    }
}
