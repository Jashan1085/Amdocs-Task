package com.amdocs.telecom.config;

import com.amdocs.telecom.dao.impl.JdbcAuthenticationDAO;
import com.amdocs.telecom.security.SessionAccountFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

import java.time.Clock;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean public UserDetailsService noPasswordOnlyLogin() {
        // Suppress Boot's generated user. Authentication is completed only after OTP.
        return username -> { throw new UsernameNotFoundException("Use the two-step sign-in flow"); };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcAuthenticationDAO dao, Clock clock) throws Exception {
        http.authorizeRequests()
                .antMatchers("/", "/login", "/login/otp", "/login/restart", "/forgot-password", "/reset-password", "/dev/otp",
                        "/css/**", "/webjars/**", "/error", "/access-denied").permitAll()
                .antMatchers("/customer/**").hasRole("CUSTOMER")
                .antMatchers("/service-desk/**").hasRole("SERVICE_DESK")
                .antMatchers("/engineer/**").hasRole("NETWORK_ENGINEER")
                .antMatchers("/manager/**").hasRole("NETWORK_MANAGER")
                .antMatchers("/dashboard").authenticated()
                .anyRequest().denyAll()
                .and().exceptionHandling()
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                .accessDeniedPage("/access-denied")
                .and().requestCache().disable()
                .httpBasic().disable().formLogin().disable()
                .logout().logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll();
        http.addFilterAfter(new SessionAccountFilter(dao, clock), SecurityContextPersistenceFilter.class);
        return http.build();
    }
}
