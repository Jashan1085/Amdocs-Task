package com.amdocs.telecom.security;

import com.amdocs.telecom.dao.impl.JdbcAuthenticationDAO;
import com.amdocs.telecom.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SessionAccountFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private TelecomPrincipal principal() {
        return new TelecomPrincipal(new AuthAccount(42, "test", "Test", "not-stored-in-session", true, 0,
                null, 0, null, null, Collections.singletonList(Role.NETWORK_MANAGER)));
    }
    @Test void logoutDoesNotDependOnDatabaseAvailability() throws Exception {
        JdbcAuthenticationDAO dao = mock(JdbcAuthenticationDAO.class);
        TelecomPrincipal user = principal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logout");
        request.setServletPath("/logout");
        MockFilterChain chain = new MockFilterChain();
        new SessionAccountFilter(dao, new MutableClock()).doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(dao);
    }
    @Test void databaseFailureFailsClosedWithoutExceptionDetails() throws Exception {
        JdbcAuthenticationDAO dao = mock(JdbcAuthenticationDAO.class);
        when(dao.findById(42, false)).thenThrow(new DataAccessResourceFailureException("private-connection-detail"));
        TelecomPrincipal user = principal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SessionAccountFilter(dao, new MutableClock()).doFilter(new MockHttpServletRequest("GET", "/manager/dashboard"), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("private-connection-detail");
        assertThat(chain.getRequest()).isNull();
    }
}
