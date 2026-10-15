package com.amdocs.telecom.dto;

import javax.validation.constraints.*;

public class OtpForm {
    @NotBlank @Pattern(regexp = "[0-9]{6}", message = "Enter the six-digit code")
    private String code;
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
}
