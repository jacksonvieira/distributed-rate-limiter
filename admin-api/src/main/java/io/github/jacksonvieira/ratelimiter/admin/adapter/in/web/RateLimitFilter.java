package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits how often one client address may call {@code /api/**}, using the project's own rate limiter.
 * Runs before authentication, so it also slows down guessing of the API key.
 */
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitResult result = limiter.tryAcquire(request.getRemoteAddr());
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        if (result.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, (result.retryAfter().toMillis() + 999) / 1000);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        logger.warn("Rate limit exceeded for " + request.getRemoteAddr() + " on " + request.getRequestURI());
        response.sendError(429, "Too many requests");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
