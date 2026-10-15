package com.amdocs.telecom.model;

import java.time.Instant;

/** Immutable persistence snapshot; lifecycle validation belongs to business services. */
public final class Customer {

    private final long customerId;
    private final String customerNumber;
    private final String customerName;
    private final String email;
    private final String mobileNumber;
    private final CustomerType customerType;
    private final String city;
    private final CustomerStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Customer(
            long customerId,
            String customerNumber,
            String customerName,
            String email,
            String mobileNumber,
            CustomerType customerType,
            String city,
            CustomerStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.customerId = customerId;
        this.customerNumber = customerNumber;
        this.customerName = customerName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.customerType = customerType;
        this.city = city;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public String getCity() {
        return city;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
