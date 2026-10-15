package com.amdocs.telecom.dto;

import javax.validation.constraints.*;

public class ResetPasswordForm {
    @NotBlank @Pattern(regexp = "[0-9]{6}", message = "Enter the six-digit code")
    private String code;
    @NotBlank @Size(min = 12, max = 72)
    private String password;
    @NotBlank @Size(min = 12, max = 72)
    private String confirmation;
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { this.password = value; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String value) { this.confirmation = value; }
}
