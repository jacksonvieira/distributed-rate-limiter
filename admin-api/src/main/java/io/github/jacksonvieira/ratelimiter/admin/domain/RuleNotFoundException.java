package io.github.jacksonvieira.ratelimiter.admin.domain;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(long id) {
        super("rule " + id + " not found");
    }
}
