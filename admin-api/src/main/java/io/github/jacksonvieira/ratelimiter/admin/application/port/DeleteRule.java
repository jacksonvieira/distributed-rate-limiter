package io.github.jacksonvieira.ratelimiter.admin.application.port;

/** Inbound port: removes a rule, failing when it does not exist. */
public interface DeleteRule {

    void execute(long id);
}
