package com.amdocs.telecom.assessment;
import com.amdocs.telecom.model.*;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AssessmentTest {
    private TroubleTicket ticket(long id,TicketStatus status,Long engineer,Instant resolved) {
        Instant created=Instant.parse("2026-10-15T12:00:00Z");
        return new TroubleTicket(id,"TT-"+id,1,engineer,IncidentCategory.NETWORK_OUTAGE,"Test",TicketPriority.CRITICAL,Severity.CRITICAL,1,status,
                created.plusSeconds(60),created.plusSeconds(600),null,resolved,null,0,created,created);
    }
    @Test void boundedStreamsDemonstrateAllRequiredAnalysesWithoutUnboundedDatabaseReads() {
        Instant now=Instant.parse("2026-10-15T12:20:00Z");
        BoundedTicketAnalytics data=new BoundedTicketAnalytics(Arrays.asList(
                new BoundedTicketAnalytics.Sample(ticket(1,TicketStatus.ASSIGNED,10L,null),"West",1),
                new BoundedTicketAnalytics.Sample(ticket(2,TicketStatus.RESOLVED,10L,now.minusSeconds(600)),"West",1),
                new BoundedTicketAnalytics.Sample(ticket(3,TicketStatus.CANCELLED,null,null),"North",2)));
        assertThat(data.byStatus()).containsEntry(TicketStatus.ASSIGNED,1L); assertThat(data.byPriority()).containsEntry(TicketPriority.CRITICAL,3L);
        assertThat(data.byRegion()).containsEntry("West",2L); assertThat(data.engineerWorkload()).containsEntry(10L,1L);
        assertThat(data.activeBreaches(now)).isEqualTo(1); assertThat(data.averageResolutionMinutes().getAsDouble()).isEqualTo(10);
        assertThat(data.topCategories()).containsExactly(IncidentCategory.NETWORK_OUTAGE); assertThat(data.engineerPerformanceMinutes()).containsEntry(10L,10.0);
        assertThat(data.repeatedIncidentCustomers()).containsExactly(1L); assertThat(TicketMetric.resolutionMinutes().units()).isEqualTo("minutes");
        assertThat(new BoundedTicketAnalytics(Collections.emptyList()).averageResolutionMinutes()).isEmpty();
        assertThatThrownBy(() -> new BoundedTicketAnalytics(Collections.nCopies(1001,new BoundedTicketAnalytics.Sample(ticket(1,TicketStatus.OPEN,null,null),"West",1)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rawJdbcExerciseRefusesNormalDevelopmentConnectionsBeforeAnySql() throws Exception {
        DataSource source=mock(DataSource.class); Connection connection=mock(Connection.class); DatabaseMetaData meta=mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:postgresql://localhost:5432/telecom_oss_dev");
        assertThatThrownBy(() -> RawJdbcAssessment.run(source)).isInstanceOf(IllegalArgumentException.class);
        verify(connection,never()).createStatement(); verify(connection,never()).prepareStatement(anyString()); verify(connection).close();
    }
}
