package com.actiongraph.events.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.events.EventApplier;
import com.actiongraph.events.ExternalEventGateway;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphEventCallbackWebAutoConfigurationTest {
    private static final Condition DONE = Condition.of("events-spring:DONE");

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphEventCallbackWebAutoConfiguration.class
            ))
            .withBean(EventApplier.class, TestEventApplier::new)
            .withPropertyValues("actiongraph.events.sweep-period=0s");

    @Test
    void callbackEndpointIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ExternalEventGateway.class);
            assertThat(context).doesNotHaveBean(ActionGraphEventCallbackController.class);
        });
    }

    @Test
    void callbackEndpointDeliversEventThroughGateway() {
        contextRunner
                .withPropertyValues("actiongraph.events.callback-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphEventCallbackController.class);
                    SuspendedRunRepository repository = context.getBean(SuspendedRunRepository.class);
                    repository.save(waitingRun());

                    mockMvc(context).perform(post("/actiongraph/events/approval.completed/APP-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.eventType").value("approval.completed"))
                            .andExpect(jsonPath("$.correlationId").value("APP-1"))
                            .andExpect(jsonPath("$.result").value("RESUMED"));

                    assertThat(repository.findByRunId("RUN-EVENT-WEB")).isEmpty();
                    assertThat(context.getBean(TraceRepository.class).findByRun("RUN-EVENT-WEB"))
                            .extracting(event -> event.type())
                            .contains(TraceEventType.EVENT_DELIVERED, TraceEventType.RUN_ENDED);
                });
    }

    @Test
    void callbackEndpointRequiresConfiguredToken() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.events.callback-endpoint.enabled=true",
                        "actiongraph.events.callback-endpoint.shared-secret=event-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphEventCallbackController.class);
                    context.getBean(SuspendedRunRepository.class).save(waitingRun());

                    mockMvc(context).perform(post("/actiongraph/events/approval.completed/APP-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    mockMvc(context).perform(post("/actiongraph/events/approval.completed/APP-1")
                                    .header("X-ActionGraph-Event-Token", "event-secret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.result").value("RESUMED"));
                });
    }

    @Test
    void callbackEndpointAcceptsCustomPathAndTokenHeader() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.events.callback-endpoint.enabled=true",
                        "actiongraph.events.callback-endpoint.path=/internal/events",
                        "actiongraph.events.callback-endpoint.token-header=X-Bank-Event-Token",
                        "actiongraph.events.callback-endpoint.shared-secret=event-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphEventCallbackController.class);
                    context.getBean(SuspendedRunRepository.class).save(waitingRun());

                    mockMvc(context).perform(post("/internal/events/approval.completed/APP-1")
                                    .header("X-Bank-Event-Token", "event-secret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.result").value("RESUMED"));
                });
    }

    private MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private SuspendedRun waitingRun() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        return SuspendedRun.waitingForEvent(
                "RUN-EVENT-WEB",
                new Goal("eventWebGoal", Set.of(DONE)),
                blackboard,
                List.of(new ActionId("events.submit")),
                List.of(),
                "waiting for event",
                "approval.completed",
                "APP-1",
                Instant.now().plusSeconds(60)
        );
    }

    private static final class TestEventApplier implements EventApplier {
        @Override
        public String eventType() {
            return "approval.completed";
        }

        @Override
        public void apply(com.actiongraph.events.EventPayload payload, Blackboard blackboard) {
            blackboard.addCondition(DONE);
        }
    }
}
