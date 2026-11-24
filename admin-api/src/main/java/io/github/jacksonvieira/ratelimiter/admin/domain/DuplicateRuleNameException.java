package io.github.jacksonvieira.ratelimiter.admin.domain;

public class DuplicateRuleNameException extends RuntimeException {

    public DuplicateRuleNameException(String name) {
        super("a rule named '" + name + "' already exists");
    }
}
