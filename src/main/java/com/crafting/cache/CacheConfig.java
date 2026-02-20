package com.crafting.cache;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

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
}
