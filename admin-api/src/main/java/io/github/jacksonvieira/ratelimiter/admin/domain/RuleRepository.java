package io.github.jacksonvieira.ratelimiter.admin.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port for rule storage, so the application layer does not know which database holds the rules.
 */
public interface RuleRepository {

    /**
     * @return all rules in evaluation order
     */
    List<Rule> findAll();

    Optional<Rule> findById(long id);

    /**
     * Stores a new rule after all existing ones.
     *
     * @throws DuplicateRuleNameException if a rule with the same name exists
     */
    Rule create(RuleDefinition definition);

    /**
     * Replaces the definition of a rule, keeping its position.
     *
     * @throws RuleNotFoundException      if no rule has the given id
     * @throws DuplicateRuleNameException if another rule already uses the name
     */
    Rule update(long id, RuleDefinition definition);

    /**
     * @return whether a rule was deleted
     */
    boolean deleteById(long id);
}
