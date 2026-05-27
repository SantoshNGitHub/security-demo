package com.demo.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process token-bucket rate limiter.
 *
 * Auth endpoints (login/signup) get a tight bucket: 10 tokens refilled every
 * minute — this is a second layer of defence on top of Nginx's rate limiting.
 * For a multi-instance deployment, replace the ConcurrentHashMap with a
 * distributed store (Redis + Bucket4j Redis backend).
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets  = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip   = clientIp(request);
        String path = request.getRequestURI();
        boolean allowed;

        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/signup")) {
            allowed = authBuckets.computeIfAbsent(ip, k -> buildBucket(10, 10)).tryConsume(1);
        } else if (path.startsWith("/api/")) {
            allowed = apiBuckets.computeIfAbsent(ip, k -> buildBucket(60, 60)).tryConsume(1);
        } else {
            chain.doFilter(request, response);
            return;
        }

        if (allowed) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded — ip={} path={}", ip, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests — please slow down.\"}");
        }
    }

    private Bucket buildBucket(int capacity, int refillPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
