package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.domain.InvalidRuleException;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Converts between the web DTOs and the domain model so the domain never leaks through the API. */
@Mapper
interface RuleWebMapper {

    RuleDefinition toDefinition(RuleRequest request);

    @Mapping(target = "name", source = "definition.name")
    @Mapping(target = "pathPrefix", source = "definition.pathPrefix")
    @Mapping(target = "plan", source = "definition.plan")
    @Mapping(target = "capacity", source = "definition.capacity")
    @Mapping(target = "refillTokens", source = "definition.refillTokens")
    @Mapping(target = "refillPeriod", source = "definition.refillPeriod")
    @Mapping(target = "algorithm", source = "definition.algorithm")
    @Mapping(target = "key", source = "definition.key")
    @Mapping(target = "keyHeader", source = "definition.keyHeader")
    RuleResponse toResponse(Rule rule);

    List<RuleResponse> toResponses(List<Rule> rules);

    default Duration parsePeriod(String period) {
        if (period == null) {
            return null;
        }
        try {
            return Duration.parse(period);
        } catch (DateTimeParseException e) {
            throw new InvalidRuleException("refillPeriod must be an ISO-8601 duration such as PT1S");
        }
    }

    default String formatPeriod(Duration period) {
        return period == null ? null : period.toString();
    }
}
