package com.demo.product.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * First filter in the chain. Attaches a unique request ID to every request for
 * end-to-end traceability and logs method, URI, status, IP, and duration.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("{} {} | status={} | ip={} | {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    clientIp(request),
                    durationMs);
            MDC.clear();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
