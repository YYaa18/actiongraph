package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoalCatalogPromptRendererTest {
    @Test
    void rendersSupportedGoalsAndParameterSchemaFromCatalog() {
        GoalCatalog catalog = new GoalCatalog();
        catalog.register(new GoalDefinition(
                new GoalType("requestOrderCancellation"),
                "Request cancellation for an order.",
                new Goal("requestOrderCancellation", Set.of(Condition.of("order", "DONE"))),
                List.of(GoalParameterDefinition.required("orderId", "Order identifier.", "O100"))
        ));

        LlmRequest request = new GoalCatalogPromptRenderer(catalog)
                .render("Cancel order O100", GoalParameters.empty());

        assertThat(request.systemPrompt()).contains("requestOrderCancellation");
        assertThat(request.systemPrompt()).contains("Request cancellation for an order.");
        assertThat(request.systemPrompt()).contains("orderId (required)");
        assertThat(request.systemPrompt()).contains("Example: O100");
        assertThat(request.systemPrompt()).contains("missingFields");
        assertThat(request.userPrompt()).contains("Cancel order O100");
    }
}
