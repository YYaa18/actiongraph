package com.actiongraph.governance;

import com.actiongraph.action.Action;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.runtime.Blackboard;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AmountAttributeContributor implements ReviewAttributeContributor {
    private final AmountExtractor amountExtractor;
    private final List<AmountLimitRule> rules;

    public AmountAttributeContributor(AmountExtractor amountExtractor, List<AmountLimitRule> rules) {
        this.amountExtractor = Objects.requireNonNull(amountExtractor, "amountExtractor");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    @Override
    public Map<String, String> contribute(Action action, Blackboard blackboard) {
        Optional<MonetaryAmount> amount = amountExtractor.extract(action, blackboard);
        if (amount.isEmpty()) {
            return Map.of();
        }
        Optional<AmountLimitRule> rule = AmountLimitRules.match(rules, action, amount.get());
        if (rule.isEmpty() || amount.get().value().compareTo(rule.get().reviewLimit()) <= 0) {
            return Map.of();
        }
        return Map.of(
                "amount", amount.get().value().toPlainString(),
                "currency", amount.get().currency(),
                "amountEscalated", "true"
        );
    }
}
