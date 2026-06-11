package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoapExecutorObservabilityTest {
    @Test
    void emitsRuntimeLifecycleObservations() {
        List<ObservationEvent> events = new ArrayList<>();
        Action action = action();
        ActionRegistry registry = registry(action);

        RunResult result = GoapExecutor.builder()
                .traceRepository(new InMemoryTraceRepository())
                .observationSink(events::add)
                .build()
                .run(new Goal("finish work", Set.of(Condition.of("DONE"))),
                        new InMemoryBlackboard(), registry.all(), registry);

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(events)
                .extracting(ObservationEvent::type)
                .contains(
                        ObservationEventType.RUN_STARTED,
                        ObservationEventType.PLAN_GENERATED,
                        ObservationEventType.POLICY_EVALUATED,
                        ObservationEventType.ACTION_STARTED,
                        ObservationEventType.ACTION_FINISHED,
                        ObservationEventType.TRACE_FLUSHED,
                        ObservationEventType.RUN_FINISHED
                );
        assertThat(events)
                .filteredOn(event -> event.type() == ObservationEventType.ACTION_FINISHED)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.actionId()).isEqualTo("demo.complete");
                    assertThat(event.tags()).containsEntry("success", "true");
                    assertThat(event.durationNanos()).isGreaterThanOrEqualTo(0);
                });
        assertThat(events)
                .filteredOn(event -> event.type() == ObservationEventType.RUN_FINISHED)
                .singleElement()
                .satisfies(event -> assertThat(event.tags())
                        .containsEntry("status", RunStatus.COMPLETED.name())
                        .containsEntry("executedActions", "1"));
    }

    @Test
    void observationSinkFailuresDoNotBreakExecutionOrTrace() {
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        Action action = action();
        ActionRegistry registry = registry(action);

        RunResult result = GoapExecutor.builder()
                .traceRepository(traceRepository)
                .observationSink(event -> {
                    throw new IllegalStateException("metrics backend unavailable");
                })
                .build()
                .run(new Goal("finish work", Set.of(Condition.of("DONE"))),
                        new InMemoryBlackboard(), registry.all(), registry);

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(traceRepository.findByRun(result.runId()))
                .extracting(event -> event.type())
                .contains(TraceEventType.RUN_STARTED, TraceEventType.RUN_ENDED);
    }

    private ActionRegistry registry(Action action) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        registry.register(action);
        return registry;
    }

    private Action action() {
        return new Action() {
            @Override
            public ActionId id() {
                return new ActionId("demo.complete");
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
