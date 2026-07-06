package com.mateforge.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final int requestsPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastCleanupMinute;

    public RateLimitFilter(@Value("${app.rate-limit.requests-per-minute}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String key = clientKey(request);
        long minute = Instant.now().getEpochSecond() / 60;
        cleanupOldWindows(minute);
        Window window = windows.compute(key, (ignored, current) ->
            current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > requestsPerMinute) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
        // FIXED: reverse-proxied deployments previously rate-limited the proxy instead of the real client IP.
    }

    private void cleanupOldWindows(long currentMinute) {
        if (currentMinute == lastCleanupMinute) {
            return;
        }
        lastCleanupMinute = currentMinute;
        windows.entrySet().removeIf(entry -> entry.getValue().minute < currentMinute - 1);
        // FIXED: the rate-limit window map grew forever because old per-IP buckets were never evicted.
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
