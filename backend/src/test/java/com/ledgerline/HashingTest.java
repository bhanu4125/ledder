package com.ledgerline;

import static org.junit.jupiter.api.Assertions.*;

import com.ledgerline.service.Hashing;
import com.ledgerline.service.RequestRateLimiter;
import org.junit.jupiter.api.Test;

class HashingTest {
    @Test
    void hashesAreStableAndCompareWithoutStringEquality() {
        String hash = Hashing.sha256("ledgerline");
        assertEquals(64, hash.length());
        assertTrue(Hashing.matches(hash, Hashing.sha256("ledgerline")));
        assertFalse(Hashing.matches(hash, Hashing.sha256("different")));
    }

    @Test
    void rateLimiterEnforcesTheConfiguredWindowLimit() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        assertTrue(limiter.allow("client", 2, 60));
        assertTrue(limiter.allow("client", 2, 60));
        assertFalse(limiter.allow("client", 2, 60));
    }
}
