package com.actiongraph.governance;

import com.actiongraph.action.Action;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.runtime.Blackboard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AmountLimitPolicy implements PermissionPolicy {
    private final AmountExtractor amountExtractor;
    private final List<AmountLimitRule> rules;

    public AmountLimitPolicy(AmountExtractor amountExtractor, List<AmountLimitRule> rules) {
        this.amountExtractor = Objects.requireNonNull(amountExtractor, "amountExtractor");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    @Override
    public boolean canExecute(Action action, Blackboard blackboard) {
        Optional<MonetaryAmount> amount = amountExtractor.extract(action, blackboard);
        if (amount.isEmpty()) {
            return true;
        }
        Optional<AmountLimitRule> rule = AmountLimitRules.match(rules, action, amount.get());
        return rule.isPresent() && amount.get().value().compareTo(rule.get().hardLimit()) <= 0;
    }
}
