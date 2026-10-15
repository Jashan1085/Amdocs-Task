package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.SLAConfigurationDAO;
import com.amdocs.telecom.exception.WorkflowException;
import com.amdocs.telecom.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SLAServiceTest {
    private final SLAConfigurationDAO dao=mock(SLAConfigurationDAO.class);
    private final Instant now=Instant.parse("2026-10-15T12:00:00Z");
    private final SLAService service=new SLAService(dao,Clock.fixed(now,ZoneOffset.UTC));
    @ParameterizedTest @CsvSource({"CRITICAL,15,120","HIGH,30,240","MEDIUM,120,720","LOW,480,2880"})
    void usesLatestPolicyAndIndependentContinuousDeadlines(TicketPriority priority,int response,int resolution) {
        when(dao.findLatestByPriority(priority)).thenReturn(Optional.of(new SLAConfiguration(priority,3,response,resolution,80,now)));
        SLAService.Targets result=service.targets(priority);
        assertThat(result.getPolicy().getPolicyVersion()).isEqualTo(3);
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getResponseDeadline()).isEqualTo(now.plusSeconds(response*60L));
        assertThat(result.getResolutionDeadline()).isEqualTo(now.plusSeconds(resolution*60L));
    }
    @Test void missingPolicyFailsClearly() {
        when(dao.findLatestByPriority(TicketPriority.HIGH)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.targets(TicketPriority.HIGH)).isInstanceOf(WorkflowException.class)
                .hasMessageContaining("SLA policy is unavailable");
    }
}
