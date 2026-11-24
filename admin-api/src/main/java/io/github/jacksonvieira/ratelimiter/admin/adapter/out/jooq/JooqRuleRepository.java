package io.github.jacksonvieira.ratelimiter.admin.adapter.out.jooq;

import static io.github.jacksonvieira.ratelimiter.admin.adapter.out.jooq.generated.Tables.RATE_LIMIT_RULE;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.select;

import io.github.jacksonvieira.ratelimiter.admin.adapter.out.jooq.generated.tables.records.RateLimitRuleRecord;
import io.github.jacksonvieira.ratelimiter.admin.domain.Algorithm;
import io.github.jacksonvieira.ratelimiter.admin.domain.DuplicateRuleNameException;
import io.github.jacksonvieira.ratelimiter.admin.domain.KeyStrategy;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
class JooqRuleRepository implements RuleRepository {

    private final DSLContext dsl;

    @Override
    public List<Rule> findAll() {
        return dsl.selectFrom(RATE_LIMIT_RULE)
                .orderBy(RATE_LIMIT_RULE.POSITION, RATE_LIMIT_RULE.ID)
                .fetch(JooqRuleRepository::toRule);
    }

    @Override
    public Optional<Rule> findById(long id) {
        return dsl.selectFrom(RATE_LIMIT_RULE)
                .where(RATE_LIMIT_RULE.ID.eq(id))
                .fetchOptional(JooqRuleRepository::toRule);
    }

    @Override
    public Rule create(RuleDefinition d) {
        try {
            return dsl.insertInto(RATE_LIMIT_RULE)
                    .set(RATE_LIMIT_RULE.POSITION, field(select(coalesce(max(RATE_LIMIT_RULE.POSITION), 0).plus(1))
                            .from(RATE_LIMIT_RULE)))
                    .set(RATE_LIMIT_RULE.NAME, d.name())
                    .set(RATE_LIMIT_RULE.PATH_PREFIX, d.pathPrefix())
                    .set(RATE_LIMIT_RULE.PLAN, d.plan())
                    .set(RATE_LIMIT_RULE.CAPACITY, d.capacity())
                    .set(RATE_LIMIT_RULE.REFILL_TOKENS, d.refillTokens())
                    .set(RATE_LIMIT_RULE.REFILL_PERIOD_MS, millis(d.refillPeriod()))
                    .set(RATE_LIMIT_RULE.ALGORITHM, name(d.algorithm()))
                    .set(RATE_LIMIT_RULE.KEY_STRATEGY, name(d.key()))
                    .set(RATE_LIMIT_RULE.KEY_HEADER, d.keyHeader())
                    .returning()
                    .fetchOne(JooqRuleRepository::toRule);
        } catch (DuplicateKeyException | IntegrityConstraintViolationException e) {
            log.warn("Rejected rule '{}': name already exists", d.name());
            throw new DuplicateRuleNameException(d.name());
        }
    }

    @Override
    public Rule update(long id, RuleDefinition d) {
        try {
            return dsl.update(RATE_LIMIT_RULE)
                    .set(RATE_LIMIT_RULE.NAME, d.name())
                    .set(RATE_LIMIT_RULE.PATH_PREFIX, d.pathPrefix())
                    .set(RATE_LIMIT_RULE.PLAN, d.plan())
                    .set(RATE_LIMIT_RULE.CAPACITY, d.capacity())
                    .set(RATE_LIMIT_RULE.REFILL_TOKENS, d.refillTokens())
                    .set(RATE_LIMIT_RULE.REFILL_PERIOD_MS, millis(d.refillPeriod()))
                    .set(RATE_LIMIT_RULE.ALGORITHM, name(d.algorithm()))
                    .set(RATE_LIMIT_RULE.KEY_STRATEGY, name(d.key()))
                    .set(RATE_LIMIT_RULE.KEY_HEADER, d.keyHeader())
                    .set(RATE_LIMIT_RULE.UPDATED_AT, OffsetDateTime.now())
                    .where(RATE_LIMIT_RULE.ID.eq(id))
                    .returning()
                    .fetchOptional(JooqRuleRepository::toRule)
                    .orElseThrow(() -> new RuleNotFoundException(id));
        } catch (DuplicateKeyException | IntegrityConstraintViolationException e) {
            log.warn("Rejected update of rule {}: name '{}' already exists", id, d.name());
            throw new DuplicateRuleNameException(d.name());
        }
    }

    @Override
    public boolean deleteById(long id) {
        return dsl.deleteFrom(RATE_LIMIT_RULE).where(RATE_LIMIT_RULE.ID.eq(id)).execute() > 0;
    }

    private static Long millis(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Rule toRule(RateLimitRuleRecord r) {
        return new Rule(
                r.getId(),
                r.getPosition(),
                new RuleDefinition(
                        r.getName(),
                        r.getPathPrefix(),
                        r.getPlan(),
                        r.getCapacity(),
                        r.getRefillTokens(),
                        r.getRefillPeriodMs() == null ? null : Duration.ofMillis(r.getRefillPeriodMs()),
                        r.getAlgorithm() == null ? null : Algorithm.valueOf(r.getAlgorithm()),
                        r.getKeyStrategy() == null ? null : KeyStrategy.valueOf(r.getKeyStrategy()),
                        r.getKeyHeader()));
    }
}
