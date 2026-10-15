package com.amdocs.telecom.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Session identity contains no password or password hash. */
public final class TelecomPrincipal implements UserDetails {
    private static final long serialVersionUID = 1L;
    private final long userId;
    private final String username;
    private final String displayName;
    private final long authVersion;
    private final Long customerId;
    private final Long engineerId;
    private final List<GrantedAuthority> authorities;

    public TelecomPrincipal(AuthAccount account) {
        userId = account.getUserId();
        username = account.getUsername();
        displayName = account.getDisplayName();
        authVersion = account.getAuthVersion();
        customerId = account.getCustomerId();
        engineerId = account.getEngineerId();
        authorities = Collections.unmodifiableList(account.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).collect(Collectors.toList()));
    }
    public long getUserId() { return userId; }
    public long getAuthVersion() { return authVersion; }
    public String getDisplayName() { return displayName; }
    public Long getCustomerId() { return customerId; }
    public Long getEngineerId() { return engineerId; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return ""; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
