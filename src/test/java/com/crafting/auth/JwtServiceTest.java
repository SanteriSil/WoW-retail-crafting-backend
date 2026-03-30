package com.crafting.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}.
 * No Spring context needed — JwtService is instantiated directly.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-that-is-at-least-32-bytes-long";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
    }

    @Nested
    @DisplayName("Token generation")
    class GenerateToken {

        @Test
        @DisplayName("contains correct Discord ID as subject")
        void containsCorrectSubject() {
            String token = jwtService.generateToken(148170052171071488L, "silkku", Role.OWNER);
            Claims claims = jwtService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("148170052171071488");
        }

        @Test
        @DisplayName("contains username claim")
        void containsUsernameClaim() {
            String token = jwtService.generateToken(12345L, "testuser", Role.ALLOWED_USER);
            Claims claims = jwtService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.get("username", String.class)).isEqualTo("testuser");
            assertThat(claims.get("role", String.class)).isEqualTo("ALLOWED_USER");
        }

        @Test
        @DisplayName("has issuedAt and expiration dates set")
        void hasDatesSet() {
            String token = jwtService.generateToken(12345L, "testuser", Role.ALLOWED_USER);
            Claims claims = jwtService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        }

        @Test
        @DisplayName("expiration is approximately 7 days from issuedAt")
        void expirationIs7Days() {
            String token = jwtService.generateToken(12345L, "testuser", Role.ALLOWED_USER);
            Claims claims = jwtService.validateToken(token);

            assertThat(claims).isNotNull();
            long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            long expected7dMs = 7 * 24 * 60 * 60 * 1000L;
            // Allow 5-second tolerance for test execution time
            assertThat(diffMs).isBetween(expected7dMs - 5_000L, expected7dMs + 5_000L);
        }

        @Test
        @DisplayName("different users produce different tokens")
        void differentUsersProduceDifferentTokens() {
            String token1 = jwtService.generateToken(111L, "user1", Role.ALLOWED_USER);
            String token2 = jwtService.generateToken(222L, "user2", Role.ALLOWED_USER);

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("Token validation")
    class ValidateToken {

        @Test
        @DisplayName("valid token returns correct claims")
        void validTokenReturnsClaims() {
            String token = jwtService.generateToken(99L, "validuser", Role.ALLOWED_USER);
            Claims claims = jwtService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("99");
            assertThat(claims.get("username", String.class)).isEqualTo("validuser");
        }

        @Test
        @DisplayName("returns null for completely invalid token string")
        void invalidTokenReturnsNull() {
            Claims claims = jwtService.validateToken("not.a.valid.jwt");
            assertThat(claims).isNull();
        }

        @Test
        @DisplayName("returns null for empty string")
        void emptyTokenReturnsNull() {
            Claims claims = jwtService.validateToken("");
            assertThat(claims).isNull();
        }

        @Test
        @DisplayName("returns null for token signed with a different key")
        void differentKeyReturnsNull() {
            JwtService otherService = new JwtService("a-completely-different-secret-key-that-is-also-at-least-32-bytes");
            String token = otherService.generateToken(12345L, "testuser", Role.ALLOWED_USER);

            // Validate with the original service (different key) → should fail
            Claims claims = jwtService.validateToken(token);
            assertThat(claims).isNull();
        }

        @Test
        @DisplayName("returns null for a truncated token")
        void truncatedTokenReturnsNull() {
            String token = jwtService.generateToken(12345L, "testuser", Role.ALLOWED_USER);
            // Chop the token in half
            String truncated = token.substring(0, token.length() / 2);

            Claims claims = jwtService.validateToken(truncated);
            assertThat(claims).isNull();
        }

        @Test
        @DisplayName("returns null for token with tampered payload")
        void tamperedPayloadReturnsNull() {
            String token = jwtService.generateToken(12345L, "testuser", Role.ALLOWED_USER);
            // Flip a character in the middle (payload section) of the JWT
            char[] chars = token.toCharArray();
            int mid = chars.length / 2;
            chars[mid] = (chars[mid] == 'a') ? 'b' : 'a';
            String tampered = new String(chars);

            Claims claims = jwtService.validateToken(tampered);
            assertThat(claims).isNull();
        }
    }

    @Nested
    @DisplayName("Key handling")
    class KeyHandling {

        @Test
        @DisplayName("short secret is padded and still works")
        void shortSecretIsPadded() {
            JwtService shortKeyService = new JwtService("short");
            String token = shortKeyService.generateToken(42L, "shortuser", Role.ALLOWED_USER);
            Claims claims = shortKeyService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("42");
        }

        @Test
        @DisplayName("exactly 32-byte secret works without padding")
        void exact32ByteSecretWorks() {
            JwtService exactService = new JwtService("abcdefghijklmnopqrstuvwxyz123456"); // exactly 32 chars
            String token = exactService.generateToken(7L, "exactuser", Role.ALLOWED_USER);
            Claims claims = exactService.validateToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("7");
        }
    }
}
