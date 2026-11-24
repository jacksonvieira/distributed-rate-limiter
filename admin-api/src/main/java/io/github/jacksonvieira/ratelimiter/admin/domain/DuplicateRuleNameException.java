package io.github.jacksonvieira.ratelimiter.admin.domain;

/** Thrown when a rule name is already taken; names are unique. */
public class DuplicateRuleNameException extends RuntimeException {

    public DuplicateRuleNameException(String name) {
        super("a rule named '" + name + "' already exists");
    }
}
