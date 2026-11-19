package io.github.jacksonvieira.ratelimiter.gateway;

import java.net.InetSocketAddress;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Extracts the identity a request is rate limited by.
 *
 * <p>Keys carry a prefix ({@code ip:}, {@code key:}) so an API key can never share a bucket with
 * an IP address that happens to have the same text.
 */
@FunctionalInterface
public interface KeyResolver {

    String resolve(ServerHttpRequest request);

    /**
     * One bucket per client IP. Uses the TCP peer address, not {@code X-Forwarded-For}: that header
     * is set by the client and trusting it would let anyone pick their own bucket.
     */
    static KeyResolver clientIp() {
        return request -> "ip:" + remoteIp(request);
    }

    /**
     * One bucket per value of {@code headerName}; requests without the header are limited by IP.
     */
    static KeyResolver header(String headerName) {
        KeyResolver fallback = clientIp();
        return request -> {
            String value = request.getHeaders().getFirst(headerName);
            return value == null || value.isBlank() ? fallback.resolve(request) : "key:" + value.strip();
        };
    }

    private static String remoteIp(ServerHttpRequest request) {
        InetSocketAddress address = request.getRemoteAddress();
        if (address == null) {
            return "unknown";
        }
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }
}
