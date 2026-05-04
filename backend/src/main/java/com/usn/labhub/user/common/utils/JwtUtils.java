package com.usn.labhub.user.common.utils;

import com.usn.labhub.user.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.jsonwebtoken.Claims.EXPIRATION;
import static javax.crypto.Cipher.SECRET_KEY;
@Component
public class JwtUtils {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    // 构造方法注入配置
    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    // 类初始化后，根据配置的字符串生成 SecretKey 对象
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 签发 Token
     * @param memberId 用户的学号/工号
     * @param roleKey  用户的角色 (admin/student)
     * @return 生成的 JWT 字符串
     */
    public String createToken(String memberId, String roleKey,Long userId) {
        long exp = System.currentTimeMillis() + jwtProperties.getTtl();

        return Jwts.builder()
                .claim("memberId", memberId)
                .claim("userId", userId)
                .claim("roleKey", roleKey)
                .expiration(new Date(exp))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Token
     * @param token 前端传来的 token
     * @return 里面包含的 Claims 数据（如果 token 被篡改或过期，这里会抛出异常）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    /**
     * 校验 Token 是否有效
     * @param token 前端传来的 token
     * @return true-有效, false-无效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token); // 如果解析不报错，说明 token 有效且未过期
            return true;
        } catch (Exception e) {
            // 解析报错（如过期、伪造、格式不对）则返回 false
            return false;
        }
    }

    /**
     * 从 Token 中获取 memberId
     * @param token 前端传来的 token
     * @return 解析出来的 memberId
     */
    public String getMemberIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("memberId", String.class);
    }
    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", String.class);
    }
}