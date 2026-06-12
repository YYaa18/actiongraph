package com.actiongraph.spring;

import com.actiongraph.ActionGraph;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.interpretation.annotation.ActionGraphGoal;
import com.actiongraph.interpretation.annotation.ActionGraphGoalSeeder;
import com.actiongraph.interpretation.annotation.FromGoalParam;
import com.actiongraph.interpretation.annotation.GoalParameter;
import com.actiongraph.runtime.RunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HelloAgentGoldenPathTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphAutoConfiguration.class))
            .withBean(HelloAgent.class);

    @Test
    void helloAgentStartsFromOneFacadeBean() {
        context.run(ctx -> assertThat(ctx.getBean(ActionGraph.class)
                .start("hello.finish", Map.of("id", "A-1"))
                .status()).isEqualTo(RunStatus.COMPLETED));
    }

    static final class HelloAgent {
        @ActionGraphGoal(type = "hello.finish", targetConditions = "hello:DONE",
                seedConditions = "hello:INPUT_PRESENT")
        void goal(@GoalParameter(name = "id", description = "Input id", example = "A-1") String ignored) {
        }

        @ActionGraphGoalSeeder(value = "hello.finish", seedConditions = "hello:INPUT_PRESENT")
        InputId seed(@FromGoalParam("id") String id) {
            return new InputId(id);
        }

        @ActionGraphAction(id = "hello.finish", preconditions = "hello:INPUT_PRESENT", effects = "hello:DONE")
        void finish(InputId input) {
        }
    }

    record InputId(String value) {
    }
}
