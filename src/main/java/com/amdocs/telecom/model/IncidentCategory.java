package com.amdocs.telecom.model;

/** Controlled values aligned with the PostgreSQL baseline. */
public enum IncidentCategory {
    NETWORK_OUTAGE,
    CALL_DROP,
    SLOW_DATA,
    NO_CONNECTIVITY,
    SIM_ISSUE,
    BILLING,
    BROADBAND,
    ROAMING,
    ENTERPRISE_LINK,
    OTHER
}
