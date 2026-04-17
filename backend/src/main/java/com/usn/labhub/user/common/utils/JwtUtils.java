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
    public String createToken(String memberId, String roleKey) {
        long exp = System.currentTimeMillis() + jwtProperties.getTtl();

        return Jwts.builder()
                .claim("memberId", memberId)
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
}