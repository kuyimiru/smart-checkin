package com.checkin.common.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration}") private long expiration;

    public String generateToken(Long userId) {
        Date now = new Date();
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withIssuedAt(now)
                .withExpiresAt(new Date(now.getTime() + expiration))
                .sign(Algorithm.HMAC256(secret));
    }

    public Long getUserIdFromToken(String token) {
        try {
            String subject = JWT.require(Algorithm.HMAC256(secret)).build().verify(token).getSubject();
            return Long.valueOf(subject);
        } catch (JWTVerificationException e) {
            throw new RuntimeException("Token验证失败", e);
        }
    }
}
