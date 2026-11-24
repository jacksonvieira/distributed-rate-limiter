package io.github.jacksonvieira.ratelimiter.admin.adapter.out.jdbc;

import io.github.jacksonvieira.ratelimiter.admin.domain.Algorithm;
import io.github.jacksonvieira.ratelimiter.admin.domain.DuplicateRuleNameException;
import io.github.jacksonvieira.ratelimiter.admin.domain.KeyStrategy;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcRuleRepository implements RuleRepository {

    private static final String COLUMNS = "id, position, name, path_prefix, plan, capacity, refill_tokens,"
            + " refill_period_ms, algorithm, key_strategy, key_header";

    private final JdbcClient jdbc;

    @Override
    public List<Rule> findAll() {
        return jdbc.sql("select " + COLUMNS + " from rate_limit_rule order by position, id")
                .query(JdbcRuleRepository::map)
                .list();
    }

    @Override
    public Optional<Rule> findById(long id) {
        return jdbc.sql("select " + COLUMNS + " from rate_limit_rule where id = :id")
                .param("id", id)
                .query(JdbcRuleRepository::map)
                .optional();
    }

    @Override
    public Rule create(RuleDefinition definition) {
        try {
            return jdbc.sql("""
                            insert into rate_limit_rule (position, name, path_prefix, plan, capacity,
                                refill_tokens, refill_period_ms, algorithm, key_strategy, key_header)
                            values ((select coalesce(max(position), 0) + 1 from rate_limit_rule),
                                :name, :pathPrefix, :plan, :capacity, :refillTokens, :refillPeriodMs,
                                :algorithm, :key, :keyHeader)
                            returning\s""" + COLUMNS)
                    .params(parameters(definition))
                    .query(JdbcRuleRepository::map)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new DuplicateRuleNameException(definition.name());
        }
    }

    @Override
    public Rule update(long id, RuleDefinition definition) {
        try {
            return jdbc.sql("""
                            update rate_limit_rule set name = :name, path_prefix = :pathPrefix, plan = :plan,
                                capacity = :capacity, refill_tokens = :refillTokens,
                                refill_period_ms = :refillPeriodMs, algorithm = :algorithm,
                                key_strategy = :key, key_header = :keyHeader, updated_at = now()
                            where id = :id
                            returning\s""" + COLUMNS)
                    .param("id", id)
                    .params(parameters(definition))
                    .query(JdbcRuleRepository::map)
                    .optional()
                    .orElseThrow(() -> new RuleNotFoundException(id));
        } catch (DuplicateKeyException e) {
            throw new DuplicateRuleNameException(definition.name());
        }
    }

    @Override
    public boolean deleteById(long id) {
        return jdbc.sql("delete from rate_limit_rule where id = :id").param("id", id).update() > 0;
    }

    private static java.util.Map<String, Object> parameters(RuleDefinition d) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("name", d.name());
        params.put("pathPrefix", d.pathPrefix());
        params.put("plan", d.plan());
        params.put("capacity", d.capacity());
        params.put("refillTokens", d.refillTokens());
        params.put("refillPeriodMs", d.refillPeriod() == null ? null : d.refillPeriod().toMillis());
        params.put("algorithm", d.algorithm() == null ? null : d.algorithm().name());
        params.put("key", d.key() == null ? null : d.key().name());
        params.put("keyHeader", d.keyHeader());
        return params;
    }

    private static Rule map(ResultSet rs, int row) throws SQLException {
        long refillMs = rs.getLong("refill_period_ms");
        Duration refillPeriod = rs.wasNull() ? null : Duration.ofMillis(refillMs);
        String algorithm = rs.getString("algorithm");
        String key = rs.getString("key_strategy");
        return new Rule(
                rs.getLong("id"),
                rs.getInt("position"),
                new RuleDefinition(
                        rs.getString("name"),
                        rs.getString("path_prefix"),
                        rs.getString("plan"),
                        nullableLong(rs, "capacity"),
                        nullableLong(rs, "refill_tokens"),
                        refillPeriod,
                        algorithm == null ? null : Algorithm.valueOf(algorithm),
                        key == null ? null : KeyStrategy.valueOf(key),
                        rs.getString("key_header")));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
