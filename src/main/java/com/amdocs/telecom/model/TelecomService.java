package com.amdocs.telecom.model;

import java.time.Instant;
import java.time.LocalDate;

/** Immutable persistence snapshot; lifecycle validation belongs to business services. */
public final class TelecomService {

    private final long serviceId;
    private final long customerId;
    private final String serviceCode;
    private final String serviceName;
    private final ServiceType serviceType;
    private final String region;
    private final LocalDate activationDate;
    private final ServiceStatus serviceStatus;
    private final Instant createdAt;
    private final Instant updatedAt;

    public TelecomService(
            long serviceId,
            long customerId,
            String serviceCode,
            String serviceName,
            ServiceType serviceType,
            String region,
            LocalDate activationDate,
            ServiceStatus serviceStatus,
            Instant createdAt,
            Instant updatedAt) {
        this.serviceId = serviceId;
        this.customerId = customerId;
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.region = region;
        this.activationDate = activationDate;
        this.serviceStatus = serviceStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getServiceId() {
        return serviceId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public String getRegion() {
        return region;
    }

    public LocalDate getActivationDate() {
        return activationDate;
    }

    public ServiceStatus getServiceStatus() {
        return serviceStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
