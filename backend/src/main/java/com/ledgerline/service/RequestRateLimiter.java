package com.ledgerline.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class RequestRateLimiter {
    private record Counter(Instant startsAt, AtomicInteger count) {}
    private final ConcurrentHashMap<String, Counter> attempts = new ConcurrentHashMap<>();

    public boolean allow(String key, int limit, int windowSeconds) {
        Instant now = Instant.now();
        if (attempts.size() > 10000) {
            attempts.entrySet().removeIf(entry -> entry.getValue().startsAt()
                    .plusSeconds(windowSeconds * 2L).isBefore(now));
        }
        Counter counter = attempts.compute(key, (ignored, current) -> current == null
                || current.startsAt().plusSeconds(windowSeconds).isBefore(now)
                ? new Counter(now, new AtomicInteger()) : current);
        return counter.count().incrementAndGet() <= limit;
    }
}
