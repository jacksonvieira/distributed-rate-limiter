package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Answers {@code 413 Payload Too Large} when a request declares a body bigger than the configured limit. */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public BodySizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            logger.warn("Rejected request with a " + request.getContentLengthLong() + " byte body: "
                    + request.getMethod() + " " + request.getRequestURI());
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }
        chain.doFilter(request, response);
    }
}
