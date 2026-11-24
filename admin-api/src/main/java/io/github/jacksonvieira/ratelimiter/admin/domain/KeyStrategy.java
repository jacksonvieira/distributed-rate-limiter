package io.github.jacksonvieira.ratelimiter.admin.domain;

/** How the gateway identifies the client a rule's limit applies to. */
public enum KeyStrategy {
    /** The client's remote address. */
    IP,
    /** The value of the rule's configured header. */
    HEADER
}
