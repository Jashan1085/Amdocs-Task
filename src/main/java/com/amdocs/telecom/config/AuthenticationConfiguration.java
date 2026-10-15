package com.amdocs.telecom.config;

import com.amdocs.telecom.security.OtpDelivery;
import com.amdocs.telecom.security.OtpDeliveryUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthenticationConfiguration {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
    @Bean @ConditionalOnMissingBean(OtpDelivery.class)
    public OtpDelivery unavailableOtpDelivery() {
        return (id, binding, code, expiry) -> { throw new OtpDeliveryUnavailableException(); };
    }
}
