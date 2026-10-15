package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.SLAConfiguration;
import com.amdocs.telecom.model.TicketPriority;
import java.util.Optional;

/** Read-only persistence access; authorization belongs in the calling service. */
public interface SLAConfigurationDAO {

    Optional<SLAConfiguration> findByPriorityAndVersion(TicketPriority priority, int policyVersion);

    Optional<SLAConfiguration> findLatestByPriority(TicketPriority priority);
}
