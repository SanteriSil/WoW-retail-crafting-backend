package com.crafting.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Simple in-memory cache that stores a single result with a configurable TTL.
 * The cache can be explicitly invalidated so the next {@link #get(Supplier)}
 * call fetches fresh data. Thread-safe via synchronisation.
 */
public class CachedResult<T> {
    private final Duration ttl;
    private T value;
    private Instant cachedAt;

    public CachedResult(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Returns the cached value if still valid, otherwise calls the supplier,
     * stores the result and returns it.
     */
    public synchronized T get(Supplier<T> supplier) {
        if (value != null && cachedAt != null && Instant.now().isBefore(cachedAt.plus(ttl))) {
            return value;
        }
        value = supplier.get();
        cachedAt = Instant.now();
        return value;
    }

    /** Marks the cache as stale so the next get() will re-query. */
    public synchronized void invalidate() {
        value = null;
        cachedAt = null;
    }
}
