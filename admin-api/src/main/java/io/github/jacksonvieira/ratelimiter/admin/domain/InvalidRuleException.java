package io.github.jacksonvieira.ratelimiter.admin.domain;

/** Thrown when a rule definition breaks a domain invariant. */
public class InvalidRuleException extends RuntimeException {

    public InvalidRuleException(String message) {
        super(message);
    }
}
