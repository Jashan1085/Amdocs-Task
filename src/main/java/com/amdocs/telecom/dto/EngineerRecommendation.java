package com.amdocs.telecom.dto;

import com.amdocs.telecom.model.NetworkEngineer;

public final class EngineerRecommendation {
    private final NetworkEngineer engineer;
    private final long activeWorkload;
    public EngineerRecommendation(NetworkEngineer engineer,long activeWorkload) { this.engineer=engineer; this.activeWorkload=activeWorkload; }
    public NetworkEngineer getEngineer() { return engineer; }
    public long getActiveWorkload() { return activeWorkload; }
}
