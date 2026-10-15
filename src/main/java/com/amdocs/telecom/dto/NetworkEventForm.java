package com.amdocs.telecom.dto;
import javax.validation.constraints.*;
public class NetworkEventForm {
    public enum Type { OUTAGE, DEGRADATION }
    @NotNull private java.util.UUID eventKey;
    @NotNull @Min(1) private Long nodeId;
    @NotNull private Type type;
    @NotBlank @Size(max=1000) private String description;
    public java.util.UUID getEventKey() { return eventKey; } public void setEventKey(java.util.UUID value) { eventKey=value; }
    public Long getNodeId() { return nodeId; } public void setNodeId(Long value) { nodeId=value; }
    public Type getType() { return type; } public void setType(Type value) { type=value; }
    public String getDescription() { return description; } public void setDescription(String value) { description=value; }
}
