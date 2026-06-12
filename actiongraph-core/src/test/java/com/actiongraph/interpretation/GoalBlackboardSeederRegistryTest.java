package com.actiongraph.interpretation;

import com.actiongraph.interpretation.annotation.GoalParameter;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoalBlackboardSeederRegistryTest {
    @Test
    void registersAutomaticSchemaSeederWhenGoalHasSchemaAndNoExplicitSeeder() {
        GoalType goalType = new GoalType("product.create");
        GoalCatalog catalog = new GoalCatalog();
        catalog.register(new GoalDefinition(
                goalType,
                "Create product",
                new Goal("create", Set.of(Condition.of("product:CREATED"))),
                List.of(GoalParameterDefinition.required("name", "Name", "键盘")),
                ProductDraft.class,
                Set.of(Condition.of("product:CREATE_REQUESTED"))
        ));
        GoalBlackboardSeederRegistry registry = new GoalBlackboardSeederRegistry();

        registry.registerDefaultSeeders(catalog);

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        registry.seed(
                GoalInterpretation.ready(
                        goalType,
                        GoalParameters.of(java.util.Map.of("name", "键盘")),
                        catalog.byType(goalType).orElseThrow().goal()
                ),
                blackboard
        );

        assertThat(blackboard.get(ProductDraft.class)).contains(new ProductDraft("键盘"));
        assertThat(blackboard.conditions()).containsExactly(Condition.of("product:CREATE_REQUESTED"));
    }

    @Test
    void registersDefaultSeederForGoalWithSeedConditionsAndNoRequiredParameters() {
        GoalType goalType = new GoalType("product.list");
        GoalCatalog catalog = new GoalCatalog();
        catalog.register(new GoalDefinition(
                goalType,
                "List products",
                new Goal("list", Set.of(Condition.of("product:LIST_DONE"))),
                List.of(),
                Set.of(Condition.of("product:LIST_REQUESTED"))
        ));
        GoalBlackboardSeederRegistry registry = new GoalBlackboardSeederRegistry();

        registry.registerDefaultSeeders(catalog);

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        registry.seed(
                GoalInterpretation.ready(goalType, GoalParameters.empty(), catalog.byType(goalType).orElseThrow().goal()),
                blackboard
        );

        assertThat(blackboard.conditions()).containsExactly(Condition.of("product:LIST_REQUESTED"));
    }

    record ProductDraft(@GoalParameter(description = "Name") String name) {
    }
}
