package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.TelecomService;
import com.amdocs.telecom.dto.PageRequest;
import java.util.List;
import java.util.Optional;

/** Read-only persistence access; authorization belongs in the calling service. */
public interface TelecomServiceDAO {

    Optional<TelecomService> findById(long serviceId);

    Optional<TelecomService> findByServiceCode(String serviceCode);

    List<TelecomService> findByCustomerId(long customerId, PageRequest page);
}
