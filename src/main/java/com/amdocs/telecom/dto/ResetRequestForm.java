package com.amdocs.telecom.dto;

import javax.validation.constraints.*;

public class ResetRequestForm {
    @NotBlank @Size(max = 100)
    private String username;
    @NotBlank @Size(max = 20)
    private String captcha;
    @NotBlank @Size(max = 36)
    private String captchaId;
    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String value) { this.captchaId = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value; }
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String value) { this.captcha = value; }
}
