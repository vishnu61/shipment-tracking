package com.logistics.tracking.security;

import com.logistics.tracking.model.Company;
import com.logistics.tracking.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMs", 86400000L);

        Company company = Company.builder().id(UUID.randomUUID()).name("Test Co").build();
        testUser = User.builder()
                .id(UUID.randomUUID()).email("test@example.com")
                .company(company).role("USER").active(true).build();
    }

    @Test
    @DisplayName("generateAccessToken - produces valid token with correct claims")
    void generateAccessToken_valid() {
        String token = jwtTokenProvider.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractUsername(token)).isEqualTo("test@example.com");
        assertThat(jwtTokenProvider.extractCompanyId(token))
                .isEqualTo(testUser.getCompany().getId());
    }

    @Test
    @DisplayName("generateRefreshToken - produces valid refresh token")
    void generateRefreshToken_valid() {
        String token = jwtTokenProvider.generateRefreshToken(testUser);
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken - returns false for tampered token")
    void validateToken_tamperedToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("validateToken - returns false for expired token")
    void validateToken_expiredToken() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", -1000L);
        String token = jwtTokenProvider.generateAccessToken(testUser);
        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken - returns false for empty token")
    void validateToken_empty() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken("not.a.jwt")).isFalse();
    }
}
