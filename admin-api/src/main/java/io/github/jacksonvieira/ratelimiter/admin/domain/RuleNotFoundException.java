package io.github.jacksonvieira.ratelimiter.admin.domain;

/** Thrown when no rule exists with the requested id. */
public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(long id) {
        super("rule " + id + " not found");
    }
}
