package com.actiongraph.graph;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphExporterTest {
    @Test
    void exportsMermaidAndDotGraphs() {
        Condition start = Condition.of("dx", "START");
        Condition done = Condition.of("dx", "DONE");
        Action action = action("demo.finish", Set.of(start), Set.of(done), true);
        ActionGraphExporter exporter = new ActionGraphExporter();

        String mermaid = exporter.toMermaid(List.of(action), new Goal("demo", Set.of(done)), Set.of(start));
        String dot = exporter.toDot(List.of(action));

        assertThat(mermaid)
                .startsWith("flowchart LR")
                .contains("c_dx_START")
                .contains("a_demo_finish")
                .contains("risk=HIGH")
                .contains("human-review")
                .contains(":::target")
                .contains("c_dx_START --> a_demo_finish")
                .contains("a_demo_finish --> c_dx_DONE");
        assertThat(dot)
                .startsWith("digraph ActionGraph")
                .contains("\"dx:START\" -> \"demo.finish\"")
                .contains("\"demo.finish\" -> \"dx:DONE\"");
    }

    private Action action(String id, Set<Condition> preconditions, Set<Condition> effects, boolean review) {
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
                return ActionRiskLevel.HIGH;
            }

            @Override
            public boolean requiresHumanReview() {
                return review;
            }

            @Override
            public ActionResult execute(ExecutionContext context) {
                return ActionResult.ok();
            }
        };
    }
}
