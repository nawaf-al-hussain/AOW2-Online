package com.aow2.server.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiting filter for authentication endpoints.
 * <p>
 * Uses a sliding-window counter per client IP address to prevent brute-force
 * attacks on login and registration endpoints.
 * <p>
 * Configuration (via constructor):
 * - maxRequests: maximum allowed requests per window
 * - windowSeconds: time window in seconds
 * <p>
 * P1 Fix: Rate limiting was absent on /api/auth/login and /api/auth/register,
 * making the server vulnerable to credential-stuffing and account-creation abuse.
 */
public class RateLimitFilter implements Filter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Maximum requests allowed per client per window. */
    private final int maxRequests;

    /** Time window duration in seconds. */
    private final int windowSeconds;

    /** Order: run before Spring Security filters (which are typically at Ordered.HIGHEST_PRECEDENCE). */
    private final int order;

    /**
     * Rate limit bucket per client IP. Key = IP, Value = [timestamp of first request in window, request count].
     */
    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

    /**
     * Creates a RateLimitFilter with the given parameters.
     *
     * @param maxRequests  max requests per window per IP
     * @param windowSeconds duration of the sliding window in seconds
     */
    public RateLimitFilter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.order = Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Only rate-limit authentication endpoints
        if (!path.equals("/api/auth/login") && !path.equals("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - windowSeconds;

        long[] bucket = buckets.compute(clientIp, (key, existing) -> {
            if (existing == null) {
                return new long[]{now, 1};
            }
            // If the window has expired, reset
            if (existing[0] < windowStart) {
                return new long[]{now, 1};
            }
            // Increment count within the current window
            existing[1]++;
            return existing;
        });

        if (bucket[1] > maxRequests) {
            LOG.warn("Rate limit exceeded for IP {} on {}: {}/{} requests in {}s window",
                    clientIp, path, bucket[1], maxRequests, windowSeconds);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts the client IP address from the request.
     * FIX (M-NEW-16): X-Forwarded-For is attacker-controllable and can be spoofed to
     * bypass rate limiting by rotating the leftmost IP. Now only trusts X-Forwarded-For
     * if the request comes from a known proxy (checked via the trusted proxy header or
     * local address). Otherwise, uses the direct remote address.
     * <p>
     * FIX (M6 from CRITICAL_ANALYSIS_REPORT.md): The previous check used
     * {@code remoteAddr.startsWith("172.")} which matches all of 172.0.0.0/8 —
     * including public addresses like 172.217.x.x (Google DNS) — instead of just
     * the private 172.16.0.0/12 range. Now uses {@link InetAddress#isSiteLocalAddress()}
     * which performs the correct RFC 1918 check.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // Use InetAddress.isSiteLocalAddress() for RFC 1918 private-range check:
        //   10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 — and loopback.
        // This avoids the previous bug where 172.217.x.x (Google public DNS) was
        // falsely treated as a trusted proxy.
        boolean isTrustedProxy = false;
        if (remoteAddr != null) {
            try {
                InetAddress addr = InetAddress.getByName(remoteAddr);
                isTrustedProxy = addr.isSiteLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress();
            } catch (java.net.UnknownHostException ignored) {
                // Should not happen for a remote-addr string, but be defensive.
                isTrustedProxy = false;
            }
        }

        if (isTrustedProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        LOG.info("RateLimitFilter initialized: {} requests per {}s on auth endpoints",
                maxRequests, windowSeconds);
    }

    @Override
    public void destroy() {
        buckets.clear();
    }
}
