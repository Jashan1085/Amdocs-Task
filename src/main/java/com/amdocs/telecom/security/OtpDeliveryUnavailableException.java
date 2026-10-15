package com.amdocs.telecom.security;

public class OtpDeliveryUnavailableException extends RuntimeException {
    public OtpDeliveryUnavailableException() { super("OTP delivery is unavailable"); }
}
