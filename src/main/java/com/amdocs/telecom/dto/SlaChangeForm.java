package com.amdocs.telecom.dto;
import com.amdocs.telecom.model.TicketPriority;
import javax.validation.constraints.*;
public class SlaChangeForm {
    @NotNull @Min(0) private Long rowVersion;
    @NotBlank @Size(max=1000) private String reason;
    private TicketPriority priority;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion=value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason=value; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority value) { priority=value; }
}
