package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gateway configuration, bound from the {@code gateway.*} properties.
 *
 * @param route     the single route this gateway serves
 * @param rateLimit the limit applied to requests on that route
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(Route route, RateLimit rateLimit) {

    public GatewayProperties {
        Objects.requireNonNull(route, "gateway.route must be configured");
        Objects.requireNonNull(rateLimit, "gateway.rate-limit must be configured");
    }

    /**
     * A route forwards every request whose path starts with {@code pathPrefix} to {@code uri}.
     *
     * @param pathPrefix      path prefix handled by this route, e.g. {@code /api}
     * @param uri             base URI of the backend service
     * @param stripPrefix     whether to remove {@code pathPrefix} before forwarding
     * @param connectTimeout  maximum time to open a connection to the backend
     * @param responseTimeout maximum time to wait for the backend to respond
     */
    public record Route(
            String pathPrefix,
            URI uri,
            @DefaultValue("true") boolean stripPrefix,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("30s") Duration responseTimeout) {

        public Route {
            Objects.requireNonNull(uri, "gateway.route.uri must be configured");
            if (pathPrefix == null || !pathPrefix.startsWith("/")) {
                throw new IllegalArgumentException("gateway.route.path-prefix must start with '/', got " + pathPrefix);
            }
            // Normalise "/api/" to "/api" so matching and stripping behave the same either way.
            if (pathPrefix.length() > 1 && pathPrefix.endsWith("/")) {
                pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
            }
        }

        /**
         * @return whether the request path belongs to this route ({@code /api} matches
         *         {@code /api} and {@code /api/users}, but not {@code /apix})
         */
        public boolean matches(String path) {
            return "/".equals(pathPrefix)
                    || path.equals(pathPrefix)
                    || path.startsWith(pathPrefix + "/");
        }

        /**
         * @return the path to send to the backend for the given (matching) request path
         */
        public String backendPath(String path) {
            if (!stripPrefix || "/".equals(pathPrefix)) {
                return path;
            }
            String stripped = path.substring(pathPrefix.length());
            return stripped.isEmpty() ? "/" : stripped;
        }
    }

    /**
     * @param capacity     largest burst allowed per key
     * @param refillTokens tokens added per {@code refillPeriod}
     * @param refillPeriod period over which {@code refillTokens} are added
     * @param key          what identifies a client
     * @param keyHeader    header holding the client key when {@code key} is {@link KeyStrategy#HEADER}
     */
    public record RateLimit(
            long capacity,
            long refillTokens,
            Duration refillPeriod,
            @DefaultValue("ip") KeyStrategy key,
            @DefaultValue("X-API-Key") String keyHeader) {

        public TokenBucketConfig toTokenBucketConfig() {
            return new TokenBucketConfig(capacity, refillTokens, refillPeriod);
        }
    }

    public enum KeyStrategy {
        /** One bucket per client IP address. */
        IP,
        /** One bucket per value of a request header (an API key), falling back to the client IP. */
        HEADER
    }
}
