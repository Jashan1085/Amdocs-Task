package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.NetworkEngineer;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import java.util.Optional;

/** Read-only persistence access; authorization belongs in the calling service. */
public interface NetworkEngineerDAO {

    Optional<NetworkEngineer> findById(long engineerId);

    Optional<NetworkEngineer> findByEmployeeCode(String employeeCode);

    List<NetworkEngineer> findAvailableBySpecializationAndRegion(String specialization, String region, PageRequest page);
}
