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
     * @param algorithm    limiting algorithm; for {@link Algorithm#FIXED_WINDOW}, {@code capacity} is the limit
     *                     per window and {@code refillPeriod} the window length ({@code refillTokens} is unused)
     * @param store        where bucket state lives; {@link Store#REDIS} shares it across gateway instances
     * @param onError      what to do with a request when the limiter itself fails (e.g. Redis is down)
     * @param redis        Redis connection settings, used when {@code store} is {@link Store#REDIS}
     */
    public record RateLimit(
            long capacity,
            long refillTokens,
            Duration refillPeriod,
            @DefaultValue("ip") KeyStrategy key,
            @DefaultValue("X-API-Key") String keyHeader,
            @DefaultValue("token-bucket") Algorithm algorithm,
            @DefaultValue("memory") Store store,
            @DefaultValue("fail-open") FailureMode onError,
            @DefaultValue Redis redis) {

        public TokenBucketConfig toTokenBucketConfig() {
            return new TokenBucketConfig(capacity, refillTokens, refillPeriod);
        }
    }

    /**
     * @param uri Redis connection URI, e.g. {@code redis://localhost:6379}
     */
    public record Redis(@DefaultValue("redis://localhost:6379") String uri) {}

    public enum Algorithm {
        TOKEN_BUCKET,
        /** Available with the in-memory store only. */
        FIXED_WINDOW,
        /** Available with the in-memory store only; same {@code capacity} / {@code refillPeriod} meaning as the fixed window. */
        SLIDING_WINDOW_LOG
    }

    public enum FailureMode {
        /** Let the request through unlimited: availability over protection. */
        FAIL_OPEN,
        /** Reject the request with {@code 503 Service Unavailable}: protection over availability. */
        FAIL_CLOSED
    }

    public enum Store {
        /** Buckets kept in this instance's memory: simple, but each gateway instance counts on its own. */
        MEMORY,
        /** Buckets kept in Redis: the limit is enforced globally across all gateway instances. */
        REDIS
    }

    public enum KeyStrategy {
        /** One bucket per client IP address. */
        IP,
        /** One bucket per value of a request header (an API key), falling back to the client IP. */
        HEADER
    }
}
