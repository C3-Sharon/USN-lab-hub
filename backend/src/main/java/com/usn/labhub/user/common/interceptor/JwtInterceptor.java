package com.usn.labhub.user.common.interceptor;

import com.usn.labhub.user.common.utils.JwtUtils;
import com.usn.labhub.user.common.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token=request.getHeader("token");
       if(token==null||!jwtUtils.validateToken(token)){
           //未授权
           response.setStatus(401);
           return false;
       }
        Claims claims=jwtUtils.parseToken(token);
        Long userId = Long.valueOf(claims.get("userId").toString());
        String memberId = claims.get("memberId", String.class);
        UserContext.setUserContext(userId,memberId);
        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.remove();
    }
}
