package com.amdocs.telecom.dto;
import javax.validation.constraints.*;
public class NetworkNodeForm {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,60}") private String code;
    @NotBlank @Size(max=120) private String name;
    @NotBlank @Size(max=80) private String region;
    public String getCode() { return code; } public void setCode(String value) { code=value; }
    public String getName() { return name; } public void setName(String value) { name=value; }
    public String getRegion() { return region; } public void setRegion(String value) { region=value; }
}
