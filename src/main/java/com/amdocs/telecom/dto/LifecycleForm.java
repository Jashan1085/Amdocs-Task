package com.amdocs.telecom.dto;
import com.amdocs.telecom.model.*;
import javax.validation.constraints.*;
public class LifecycleForm {
    @NotNull @Min(0) private Long rowVersion;
    @NotNull private TicketAction action;
    @Size(max=1000) private String reason;
    @Size(max=1000) private String rootCause;
    @Size(max=4000) private String resolutionText;
    private ResolutionCode resolutionCode;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion=value; }
    public TicketAction getAction() { return action; }
    public void setAction(TicketAction value) { action=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String value) { rootCause=value; }
    public String getResolutionText() { return resolutionText; }
    public void setResolutionText(String value) { resolutionText=value; }
    public ResolutionCode getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(ResolutionCode value) { resolutionCode=value; }
}
