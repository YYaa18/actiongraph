package com.actiongraph.governance;

import com.actiongraph.action.Action;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class AmountLimitRules {
    private AmountLimitRules() {
    }

    static Optional<AmountLimitRule> match(List<AmountLimitRule> rules, Action action, MonetaryAmount amount) {
        return rules.stream()
                .filter(rule -> rule.currency().equals(amount.currency()))
                .filter(rule -> rule.matchesAction(action.id().value()))
                .min(Comparator.comparingInt(rule -> AmountLimitRule.ANY_ACTION.equals(rule.actionId()) ? 1 : 0));
    }
}
