package com.crafting.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private static final long EXPIRATION_HOURS = 168;

    public JwtService(@Value("${jwt.secret}") String secret) {
        // Pad or hash the secret to ensure it's at least 256 bits for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a JWT for the given Discord user.
     * The {@code role} claim is a hint for the frontend only (§4.6).
     * JwtAuthFilter re-verifies the actual role from DB/config on every request.
     */
    public String generateToken(long discordId, String discordUsername, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(discordId))
                .claim("username", discordUsername)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * Validates the token and returns the claims if valid, null otherwise.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
