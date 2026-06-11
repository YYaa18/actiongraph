package com.actiongraph.action;

import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.planning.Condition;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultActionRegistryTest {
    @Test
    void duplicateActionIdThrows() {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        Action action = action("same.id");

        registry.register(action);

        assertThatThrownBy(() -> registry.register(action))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("same.id");
    }

    private Action action(String id) {
        return new Action() {
            @Override
            public ActionId id() {
                return new ActionId(id);
            }

            @Override
            public Set<Class<?>> inputTypes() {
                return Set.of();
            }

            @Override
            public Set<Class<?>> outputTypes() {
                return Set.of();
            }

            @Override
            public Set<Condition> preconditions() {
                return Set.of();
            }

            @Override
            public Set<Condition> effects() {
                return Set.of(Condition.of("DONE"));
            }

            @Override
            public int cost() {
                return 1;
            }

            @Override
            public ActionRiskLevel riskLevel() {
                return ActionRiskLevel.LOW;
            }

            @Override
            public boolean requiresHumanReview() {
                return false;
            }

            @Override
            public ActionResult execute(ExecutionContext context) {
                return ActionResult.ok();
            }
        };
    }
}
