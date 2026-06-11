package com.actiongraph.validation;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.Blackboard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphValidatorTest {
    private static final Condition START = Condition.of("dx", "START");
    private static final Condition MIDDLE = Condition.of("dx", "MIDDLE");
    private static final Condition DONE = Condition.of("dx", "DONE");
    private static final GoalType GOAL_TYPE = new GoalType("demoGoal");

    @Test
    void validatesReachableGoalAndProducesPreviewPlan() {
        GoalCatalog catalog = new GoalCatalog();
        catalog.register(new GoalDefinition(
                GOAL_TYPE,
                "Demo",
                new Goal("demo", Set.of(DONE)),
                List.of(GoalParameterDefinition.required("id", "Identifier", "D1")),
                Set.of(START)
        ));

        ValidationReport report = new ActionGraphValidator().validate(catalog, List.of(
                action("a.load", Set.of(START), Set.of(MIDDLE)),
                action("b.finish", Set.of(MIDDLE), Set.of(DONE))
        ));

        assertThat(report.valid()).isTrue();
        assertThat(report.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.reachable()).isTrue();
            assertThat(goal.previewPlan())
                    .extracting(step -> step.actionId().value())
                    .containsExactly("a.load", "b.finish");
            assertThat(goal.missingConditions()).isEmpty();
            assertThat(goal.danglingActions()).isEmpty();
        });
    }

    @Test
    void reportsMissingConditionsDanglingActionsAndClosestEffect() {
        Condition typo = Condition.of("dx", "DNOE");
        GoalCatalog catalog = new GoalCatalog();
        catalog.register(new GoalDefinition(
                GOAL_TYPE,
                "Demo",
                new Goal("demo", Set.of(DONE)),
                List.of(),
                Set.of(START)
        ));

        ValidationReport report = new ActionGraphValidator().validate(catalog, List.of(
                action("a.load", Set.of(START), Set.of(MIDDLE)),
                action("b.typo", Set.of(MIDDLE), Set.of(typo)),
                action("c.dangling", Set.of(DONE), Set.of(Condition.of("dx", "AFTER_DONE")))
        ));

        assertThat(report.valid()).isFalse();
        assertThat(report.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.reachable()).isFalse();
            assertThat(goal.missingConditions()).containsExactly(DONE);
            assertThat(goal.danglingActions())
                    .extracting(ActionId::value)
                    .containsExactly("c.dangling");
            assertThat(goal.diagnosticText())
                    .contains("goal 'demoGoal' unreachable")
                    .contains("dx:DONE")
                    .contains("closest registered effect")
                    .contains("dx:DNOE")
                    .contains("c.dangling");
        });
    }

    @Test
    void seederConformanceChecksDeclaredSeedConditions() {
        GoalDefinition definition = new GoalDefinition(
                GOAL_TYPE,
                "Demo",
                new Goal("demo", Set.of(DONE)),
                List.of(),
                Set.of(START)
        );
        GoalBlackboardSeeder seeder = new GoalBlackboardSeeder() {
            @Override
            public GoalType goalType() {
                return GOAL_TYPE;
            }

            @Override
            public void seed(GoalParameters parameters, Blackboard blackboard) {
                blackboard.addCondition(START);
            }
        };

        assertThatCode(() -> SeederConformance.assertSeedsDeclaredConditions(seeder, GoalParameters.empty(), definition))
                .doesNotThrowAnyException();

        GoalDefinition drifted = new GoalDefinition(GOAL_TYPE, "Demo", new Goal("demo", Set.of(DONE)), List.of(),
                Set.of(START, MIDDLE));
        assertThatThrownBy(() -> SeederConformance.assertSeedsDeclaredConditions(seeder, GoalParameters.empty(), drifted))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("dx:MIDDLE");
    }

    private Action action(String id, Set<Condition> preconditions, Set<Condition> effects) {
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
                return preconditions;
            }

            @Override
            public Set<Condition> effects() {
                return effects;
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

            @Override
            public CompensationResult compensate(ExecutionContext context) {
                return CompensationResult.noop();
            }
        };
    }
}
