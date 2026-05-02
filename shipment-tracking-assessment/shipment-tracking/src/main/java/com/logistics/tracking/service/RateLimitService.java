package com.logistics.tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    @PersistenceContext
    private EntityManager em;

    @Value("${app.rate-limit.requests-per-minute:1000}")
    private int requestsPerMinute;

    /**
     * Checks and increments the rate limit counter for a company within the current 1-minute window.
     * Uses an upsert to handle concurrent requests safely.
     *
     * @return true if the request is allowed, false if rate limit exceeded
     */
    @Transactional
    public boolean isAllowed(UUID companyId) {
        Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        // Atomic upsert: insert or increment
        int updated = em.createNativeQuery("""
            INSERT INTO api_rate_limits (id, company_id, window_start, request_count, window_seconds, max_requests, created_at, updated_at)
            VALUES (uuid_generate_v4(), :companyId, :windowStart, 1, 60, :maxRequests, NOW(), NOW())
            ON CONFLICT (company_id, window_start)
            DO UPDATE SET request_count = api_rate_limits.request_count + 1,
                          updated_at    = NOW()
            WHERE api_rate_limits.request_count < :maxRequests
            """)
            .setParameter("companyId", companyId)
            .setParameter("windowStart", windowStart)
            .setParameter("maxRequests", requestsPerMinute)
            .executeUpdate();

        if (updated == 0) {
            log.warn("Rate limit exceeded for company {}", companyId);
            return false;
        }
        return true;
    }

    /**
     * Returns remaining requests in the current window.
     */
    @Transactional(readOnly = true)
    public int getRemainingRequests(UUID companyId) {
        Instant windowStart = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Object count = em.createNativeQuery(
            "SELECT request_count FROM api_rate_limits WHERE company_id = :companyId AND window_start = :windowStart")
            .setParameter("companyId", companyId)
            .setParameter("windowStart", windowStart)
            .getSingleResultOrNull();

        int used = count != null ? ((Number) count).intValue() : 0;
        return Math.max(0, requestsPerMinute - used);
    }
}
