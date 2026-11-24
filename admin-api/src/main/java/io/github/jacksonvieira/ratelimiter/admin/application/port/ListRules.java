package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import java.util.List;

/** Inbound port: lists the rules in evaluation order. */
public interface ListRules {

    List<Rule> execute();
}
