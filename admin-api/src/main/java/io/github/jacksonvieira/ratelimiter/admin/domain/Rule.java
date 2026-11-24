package io.github.jacksonvieira.ratelimiter.admin.domain;

/**
 * A stored rule. Rules are evaluated by {@code position}, lowest first, and the first match wins.
 */
public record Rule(long id, int position, RuleDefinition definition) {}
