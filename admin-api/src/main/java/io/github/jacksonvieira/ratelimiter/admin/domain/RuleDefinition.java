package io.github.jacksonvieira.ratelimiter.admin.domain;

import java.time.Duration;

/**
 * What a rule says: which requests it matches and which limit settings it overrides. Mirrors the gateway's
 * {@code gateway.rules} entries; every field except {@code name} is optional.
 */
public record RuleDefinition(
        String name,
        String pathPrefix,
        String plan,
        Long capacity,
        Long refillTokens,
        Duration refillPeriod,
        Algorithm algorithm,
        KeyStrategy key,
        String keyHeader) {

    public RuleDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a rule needs a name");
        }
        if (pathPrefix != null && !pathPrefix.startsWith("/")) {
            throw new IllegalArgumentException("pathPrefix must start with '/', got " + pathPrefix);
        }
        if (pathPrefix != null && pathPrefix.length() > 1 && pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
        }
        requirePositive("capacity", capacity);
        requirePositive("refillTokens", refillTokens);
        if (refillPeriod != null && (refillPeriod.isZero() || refillPeriod.isNegative())) {
            throw new IllegalArgumentException("refillPeriod must be positive, got " + refillPeriod);
        }
    }

    private static void requirePositive(String field, Long value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException(field + " must be at least 1, got " + value);
        }
    }
}
