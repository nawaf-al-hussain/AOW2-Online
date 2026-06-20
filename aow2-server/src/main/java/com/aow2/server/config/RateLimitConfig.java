package com.aow2.server.config;

import com.aow2.server.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the rate limiting filter for authentication endpoints.
 * <p>
 * P1 Fix: Adds brute-force protection on /api/auth/login and /api/auth/register.
 * Allows 5 requests per 60 seconds per IP address.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Registers the rate limit filter as a servlet filter.
     * Applied before Spring Security filter chain.
     *
     * @return the filter registration bean
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        // 5 attempts per 60 seconds per IP — prevents brute-force while allowing normal usage
        registration.setFilter(new RateLimitFilter(5, 60));
        registration.addUrlPatterns("/api/auth/login", "/api/auth/register");
        registration.setOrder(Integer.MIN_VALUE); // highest priority
        registration.setName("rateLimitFilter");
        return registration;
    }
}
