package com.amdocs.telecom.dto;

import javax.validation.constraints.*;

public class AssignmentForm {
    @NotNull @Positive private Long engineerId;
    @NotNull @Min(0) private Long rowVersion;
    @Size(max=1000) private String reason;
    public Long getEngineerId() { return engineerId; }
    public void setEngineerId(Long value) { engineerId=value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
}
