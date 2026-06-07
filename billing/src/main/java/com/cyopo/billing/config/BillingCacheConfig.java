package com.cyopo.billing.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.client.RestTemplate;

/**
 * Cache config for billing module.
 * Uses simple in-memory cache for GeoIP results.
 * Replace with Redis if scaling to multiple instances.
 */
@Configuration
@EnableCaching
public class BillingCacheConfig {

    @Bean
    public CacheManager billingCacheManager() {
        // geoip cache — stores IP → CountryInfo for 24h
        // Simple in-memory for now; swap for RedisCacheManager in production
        return new ConcurrentMapCacheManager("geoip");
    }
}