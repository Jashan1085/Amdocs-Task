package com.amdocs.telecom.security;

import com.amdocs.telecom.dao.impl.JdbcAuthenticationDAO;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;

/** Reject sessions revoked by reset, disablement, role changes or account lock. */
public final class SessionAccountFilter extends OncePerRequestFilter {
    private final JdbcAuthenticationDAO dao;
    private final Clock clock;
    public SessionAccountFilter(JdbcAuthenticationDAO dao, Clock clock) { this.dao = dao; this.clock = clock; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // Sign-out must remain available even while the database is unavailable.
        return "/logout".equals(request.getServletPath());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TelecomPrincipal) {
            TelecomPrincipal principal = (TelecomPrincipal) authentication.getPrincipal();
            try {
                AuthAccount current = dao.findById(principal.getUserId(), false).orElse(null);
                Set<String> roles = principal.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
                if (current == null || !current.isEnabled() || current.getAuthVersion() != principal.getAuthVersion()
                        || (current.getLockedUntil() != null && clock.instant().isBefore(current.getLockedUntil()))
                        || !roles.equals(current.getRoles().stream().map(r -> "ROLE_" + r.name()).collect(Collectors.toSet()))) {
                    SecurityContextHolder.clearContext();
                    if (request.getSession(false) != null) { request.getSession(false).invalidate(); }
                    response.sendRedirect(request.getContextPath() + "/login?expired");
                    return;
                }
            } catch (DataAccessException exception) {
                response.sendError(503);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
