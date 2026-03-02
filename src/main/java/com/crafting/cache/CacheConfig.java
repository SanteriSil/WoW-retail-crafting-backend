package com.crafting.cache;

import com.crafting.auth.Role;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class CacheConfig {

    @Bean
    public CachedResult<List<Item>> itemCache() {
        return new CachedResult<>(Duration.ofMinutes(30));
    }

    @Bean
    public CachedResult<List<Long>> itemIdCache() {
        return new CachedResult<>(Duration.ofMinutes(30));
    }

    @Bean
    public CachedResult<List<Profession>> professionCache() {
        return new CachedResult<>(Duration.ofHours(72));
    }

    /**
     * Short-lived cache for allowed_users role lookups (PLAN.md §12.1).
     * Refreshed from DB at most every 30 seconds, or immediately on any
     * user-management write (add / remove / promote / demote).
     */
    @Bean
    public CachedResult<Map<Long, Role>> roleLookupCache() {
        return new CachedResult<>(Duration.ofSeconds(30));
    }
}
