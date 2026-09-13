package com.usn.labhub.user.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.auth.AuthException;
import com.usn.labhub.user.common.auth.AuthReason;
import com.usn.labhub.user.common.auth.AuthResponseWriter;
import com.usn.labhub.user.common.auth.AuthenticatedAccount;
import com.usn.labhub.user.common.auth.AuthenticationService;
import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.interceptor.JwtInterceptor;
import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.common.utils.UserContext;
import com.usn.labhub.user.config.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {

    private AuthenticationService authenticationService;
    private JwtUtils jwtUtils;
    private JwtInterceptor interceptor;
    private HandlerMethod openHandler;
    private HandlerMethod adminHandler;

    @BeforeEach
    void setUp() throws Exception {
        authenticationService = mock(AuthenticationService.class);
        jwtUtils = jwtUtils(60_000);
        interceptor = new JwtInterceptor(jwtUtils, authenticationService, new AuthResponseWriter(new ObjectMapper()));
        openHandler = handler("open");
        adminHandler = handler("adminOnly");
    }

    @AfterEach
    void clearContext() {
        UserContext.remove();
    }

    @Test
    void missingTokenReturnsStructured401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, openHandler));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_MISSING"));
    }

    @Test
    void authorizationBearerLoadsCurrentDatabaseAccount() throws Exception {
        String token = jwtUtils.createToken("20260001", "MEMBER", 2L);
        when(authenticationService.authenticate(2L, "20260001"))
                .thenReturn(new AuthenticatedAccount(2L, "20260001", Set.of("MEMBER")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), openHandler));

        verify(authenticationService).authenticate(2L, "20260001");
        assertEquals(Set.of("MEMBER"), UserContext.getRoles());
    }

    @Test
    void legacyTokenHeaderRemainsCompatible() throws Exception {
        String token = jwtUtils.createToken("20260001", "MEMBER", 2L);
        when(authenticationService.authenticate(2L, "20260001"))
                .thenReturn(new AuthenticatedAccount(2L, "20260001", Set.of("MEMBER")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", token);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), openHandler));
    }

    @Test
    void conflictingHeadersReturnTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer first");
        request.addHeader("token", "second");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, openHandler));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_INVALID"));
    }

    @Test
    void malformedTokenReturnsTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, openHandler));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_INVALID"));
    }

    @Test
    void expiredTokenReturnsTokenExpired() throws Exception {
        String token = jwtUtils(-1).createToken("20260001", "MEMBER", 2L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, openHandler));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_EXPIRED"));
    }

    @Test
    void disabledAccountReasonIsPreserved() throws Exception {
        String token = jwtUtils.createToken("20260001", "MEMBER", 2L);
        when(authenticationService.authenticate(2L, "20260001"))
                .thenThrow(AuthException.unauthorized(AuthReason.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, openHandler));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_DISABLED"));
    }

    @Test
    void insufficientRoleReturnsStructured403() throws Exception {
        String token = jwtUtils.createToken("20260001", "MEMBER", 2L);
        when(authenticationService.authenticate(2L, "20260001"))
                .thenReturn(new AuthenticatedAccount(2L, "20260001", Set.of("MEMBER")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, adminHandler));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCESS_DENIED"));
    }

    @Test
    void sufficientRolePassesAuthorizationGate() throws Exception {
        String token = jwtUtils.createToken("admin", "SYSTEM_ADMIN", 1L);
        when(authenticationService.authenticate(1L, "admin"))
                .thenReturn(new AuthenticatedAccount(1L, "admin", Set.of("SYSTEM_ADMIN")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), adminHandler));
    }

    @Test
    void optionsRequestDoesNotRequireToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/workbench/overview");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), openHandler));
    }

    private JwtUtils jwtUtils(long ttl) {
        JwtProperties properties = new JwtProperties();
        properties.setSecretKey("test-secret-key-that-is-long-enough-for-hmac-sha-256");
        properties.setTtl(ttl);
        JwtUtils utils = new JwtUtils(properties);
        utils.init();
        return utils;
    }

    private HandlerMethod handler(String methodName) throws Exception {
        Method method = SecuredHandler.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new SecuredHandler(), method);
    }

    private static class SecuredHandler {
        public void open() {
        }

        @RequireRoles("SYSTEM_ADMIN")
        public void adminOnly() {
        }
    }
}
