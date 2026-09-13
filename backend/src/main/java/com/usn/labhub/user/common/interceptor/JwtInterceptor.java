package com.usn.labhub.user.common.interceptor;

import com.usn.labhub.user.common.auth.AuthException;
import com.usn.labhub.user.common.auth.AuthReason;
import com.usn.labhub.user.common.auth.AuthResponseWriter;
import com.usn.labhub.user.common.auth.AuthenticatedAccount;
import com.usn.labhub.user.common.auth.AuthenticationService;
import com.usn.labhub.user.common.auth.RequireRoles;
import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.common.utils.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final AuthenticationService authenticationService;
    private final AuthResponseWriter responseWriter;

    public JwtInterceptor(JwtUtils jwtUtils, AuthenticationService authenticationService,
                          AuthResponseWriter responseWriter) {
        this.jwtUtils = jwtUtils;
        this.authenticationService = authenticationService;
        this.responseWriter = responseWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token;
        try {
            token = resolveToken(request);
        } catch (AuthException e) {
            responseWriter.write(response, e.getHttpStatus(), e.getMessage(), e.getReason());
            return false;
        }

        Claims claims;
        try {
            claims = jwtUtils.parseToken(token);
        } catch (ExpiredJwtException e) {
            responseWriter.write(response, 401, "登录状态已失效，请重新登录", AuthReason.TOKEN_EXPIRED);
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            responseWriter.write(response, 401, "登录状态已失效，请重新登录", AuthReason.TOKEN_INVALID);
            return false;
        }

        Long userId;
        String memberId;
        try {
            userId = Long.valueOf(String.valueOf(claims.get("userId")));
            memberId = claims.get("memberId", String.class);
        } catch (RuntimeException e) {
            responseWriter.write(response, 401, "登录状态已失效，请重新登录", AuthReason.TOKEN_INVALID);
            return false;
        }

        AuthenticatedAccount account;
        try {
            account = authenticationService.authenticate(userId, memberId);
        } catch (AuthException e) {
            responseWriter.write(response, e.getHttpStatus(), e.getMessage(), e.getReason());
            return false;
        }

        Set<String> requiredRoles = requiredRoles(handler);
        if (!requiredRoles.isEmpty() && account.roles().stream().noneMatch(requiredRoles::contains)) {
            responseWriter.write(response, 403, "无权访问该资源", AuthReason.ACCESS_DENIED);
            return false;
        }

        UserContext.setUserContext(account.userId(), account.memberId(), account.roles());
        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.remove();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = trimToNull(request.getHeader("Authorization"));
        String legacyToken = trimToNull(request.getHeader("token"));
        String bearerToken = null;

        if (authorization != null) {
            if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "登录状态已失效，请重新登录");
            }
            bearerToken = trimToNull(authorization.substring(7));
            if (bearerToken == null) {
                throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "登录状态已失效，请重新登录");
            }
        }

        if (bearerToken != null && legacyToken != null && !bearerToken.equals(legacyToken)) {
            throw AuthException.unauthorized(AuthReason.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        String token = bearerToken != null ? bearerToken : legacyToken;
        if (token == null) {
            throw AuthException.unauthorized(AuthReason.TOKEN_MISSING, "登录状态已失效，请重新登录");
        }
        return token;
    }

    private Set<String> requiredRoles(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return Set.of();
        }
        RequireRoles annotation = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
        }
        return annotation == null ? Set.of() : Set.copyOf(Arrays.asList(annotation.value()));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
