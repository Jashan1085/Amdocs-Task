package com.amdocs.telecom.dto;

import com.amdocs.telecom.model.*;
import javax.validation.constraints.*;

public class TicketCreateForm {
    @NotNull @Positive private Long serviceId;
    @NotNull private IncidentCategory category;
    @NotNull private TicketPriority priority;
    @NotNull private Severity severity;
    @NotBlank @Size(max = 4000) private String description;
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long value) { serviceId = value; }
    public IncidentCategory getCategory() { return category; }
    public void setCategory(IncidentCategory value) { category = value; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority value) { priority = value; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity value) { severity = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
