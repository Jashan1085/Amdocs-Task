package com.amdocs.telecom.model;

import java.time.Instant;

/** Immutable persistence snapshot; lifecycle validation belongs to business services. */
public final class NetworkEngineer {

    private final long engineerId;
    private final String employeeCode;
    private final String engineerName;
    private final String specialization;
    private final String region;
    private final int experienceYears;
    private final boolean available;
    private final Instant createdAt;
    private final Instant updatedAt;

    public NetworkEngineer(
            long engineerId,
            String employeeCode,
            String engineerName,
            String specialization,
            String region,
            int experienceYears,
            boolean available,
            Instant createdAt,
            Instant updatedAt) {
        this.engineerId = engineerId;
        this.employeeCode = employeeCode;
        this.engineerName = engineerName;
        this.specialization = specialization;
        this.region = region;
        this.experienceYears = experienceYears;
        this.available = available;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getEngineerId() {
        return engineerId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getEngineerName() {
        return engineerName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getRegion() {
        return region;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
