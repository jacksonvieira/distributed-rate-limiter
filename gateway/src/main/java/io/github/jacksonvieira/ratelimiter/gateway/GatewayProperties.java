package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gateway configuration, bound from the {@code gateway.*} properties.
 *
 * @param route     the single route this gateway serves
 * @param rateLimit the default limit applied to requests on that route
 * @param rules     optional overrides of the default limit, checked in order; the first match wins
 * @param planHeader header whose value is matched against {@link Rule#plan()}
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(
        Route route, RateLimit rateLimit, @DefaultValue List<Rule> rules, @DefaultValue("X-Plan") String planHeader) {

    public GatewayProperties {
        Objects.requireNonNull(route, "gateway.route must be configured");
        Objects.requireNonNull(rateLimit, "gateway.rate-limit must be configured");
        rules = rules == null ? List.of() : List.copyOf(rules);
        Set<String> names = new HashSet<>();
        for (Rule rule : rules) {
            if (!names.add(rule.name())) {
                throw new IllegalArgumentException("gateway.rules has a duplicate name: " + rule.name());
            }
        }
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
     * Overrides parts of the default {@link RateLimit} for the requests it matches. Every field except
     * {@code name} is optional; a missing limit setting is inherited from {@code gateway.rate-limit}.
     *
     * @param name         unique identifier; also scopes the rule's counters
     * @param pathPrefix   matches requests whose full request path (including the route prefix) starts with it, segment-wise; any path when absent
     * @param plan         matches requests whose plan header equals it; any plan when absent
     * @param capacity     overrides {@link RateLimit#capacity()}
     * @param refillTokens overrides {@link RateLimit#refillTokens()}
     * @param refillPeriod overrides {@link RateLimit#refillPeriod()}
     * @param algorithm    overrides {@link RateLimit#algorithm()}
     * @param key          overrides {@link RateLimit#key()}
     * @param keyHeader    overrides {@link RateLimit#keyHeader()}
     */
    public record Rule(
            String name,
            String pathPrefix,
            String plan,
            Long capacity,
            Long refillTokens,
            Duration refillPeriod,
            Algorithm algorithm,
            KeyStrategy key,
            String keyHeader) {

        public Rule {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("every gateway.rules entry needs a name");
            }
            if (pathPrefix != null && !pathPrefix.startsWith("/")) {
                throw new IllegalArgumentException(
                        "gateway.rules[" + name + "].path-prefix must start with '/', got " + pathPrefix);
            }
            if (pathPrefix != null && pathPrefix.length() > 1 && pathPrefix.endsWith("/")) {
                pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
            }
        }

        public boolean matches(String path, String requestPlan) {
            boolean pathMatches = pathPrefix == null
                    || "/".equals(pathPrefix)
                    || path.equals(pathPrefix)
                    || path.startsWith(pathPrefix + "/");
            return pathMatches && (plan == null || plan.equals(requestPlan));
        }

        /**
         * @return the default limit with this rule's overrides applied
         */
        public RateLimit applyTo(RateLimit base) {
            return new RateLimit(
                    capacity != null ? capacity : base.capacity(),
                    refillTokens != null ? refillTokens : base.refillTokens(),
                    refillPeriod != null ? refillPeriod : base.refillPeriod(),
                    key != null ? key : base.key(),
                    keyHeader != null ? keyHeader : base.keyHeader(),
                    algorithm != null ? algorithm : base.algorithm(),
                    base.store(),
                    base.onError(),
                    base.redis());
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
        SLIDING_WINDOW_LOG,
        SLIDING_WINDOW_COUNTER
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
