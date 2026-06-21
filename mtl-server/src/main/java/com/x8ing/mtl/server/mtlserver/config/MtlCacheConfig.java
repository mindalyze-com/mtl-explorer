package com.x8ing.mtl.server.mtlserver.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class MtlCacheConfig {

    public static final String LOCATION_SEARCH_STATUS_CACHE = "locationSearchStatus";
    public static final Duration LOCATION_SEARCH_STATUS_TTL = Duration.ofSeconds(10);
    public static final long LOCATION_SEARCH_STATUS_MAXIMUM_SIZE = 1;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(LOCATION_SEARCH_STATUS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(LOCATION_SEARCH_STATUS_TTL)
                .maximumSize(LOCATION_SEARCH_STATUS_MAXIMUM_SIZE));
        return cacheManager;
    }
}
