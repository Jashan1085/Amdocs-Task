package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.Customer;
import java.util.Optional;

/** Read-only persistence access; authorization belongs in the calling service. */
public interface CustomerDAO {

    Optional<Customer> findById(long customerId);

    Optional<Customer> findByCustomerNumber(String customerNumber);
}
