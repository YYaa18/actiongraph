package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewResult;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GoapExecutorHumanReviewMetadataTest {
    @Test
    void runMetadataIsPropagatedToHumanReviewAttributes() {
        CapturingHumanReviewPolicy reviewPolicy = new CapturingHumanReviewPolicy();
        Action action = highRiskAction();
        ActionRegistry registry = registry(action);
        GoapExecutor executor = GoapExecutor.builder()
                .traceRepository(new InMemoryTraceRepository())
                .humanReviewPolicy(reviewPolicy)
                .reviewAttributeContributor((reviewAction, blackboard) -> Map.of(
                        "business.amount", "120000",
                        "requestHeader.X-Source-System", "contributor-overrides"
                ))
                .maskingPolicy(new TokenMaskingPolicy())
                .build();

        RunResult result = executor.run(
                new Goal("review metadata", Set.of(Condition.of("DONE"))),
                new InMemoryBlackboard(),
                registry.all(),
                registry,
                Map.of(
                        "requestHeader.X-Request-Id", "REQ-42",
                        "requestHeader.Authorization", "Bearer test-token",
                        "requestHeader.X-Source-System", "legacy-core"
                )
        );

        assertThat(result.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(reviewPolicy.request()).isNotNull();
        assertThat(reviewPolicy.request().attributes())
                .containsEntry("requestHeader.X-Request-Id", "REQ-42")
                .containsEntry("requestHeader.Authorization", "[MASKED]")
                .containsEntry("requestHeader.X-Source-System", "contributor-overrides")
                .containsEntry("business.amount", "120000");
    }

    @Test
    void requestedByPersistsAcrossHumanReviewSuspendAndResumeWhileActedByIsTracedSeparately() {
        CapturingHumanReviewPolicy reviewPolicy = new CapturingHumanReviewPolicy();
        PrincipalCapturingAction action = new PrincipalCapturingAction();
        ActionRegistry registry = registry(action);
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        GoapExecutor executor = GoapExecutor.builder()
                .traceRepository(traceRepository)
                .suspendedRunRepository(suspendedRuns)
                .humanReviewPolicy(reviewPolicy)
                .build();
        RunPrincipal requester = new RunPrincipal("user:alice", "portal-web", List.of("channel:mobile"),
                Map.of("roles", "maker"));
        RunPrincipal approver = new RunPrincipal("user:bob", "approval-console", List.of(), Map.of("roles", "checker"));

        RunResult suspended = executor.run(
                new Goal("review principal", Set.of(Condition.of("DONE"))),
                new InMemoryBlackboard(),
                registry.all(),
                registry,
                Map.of(),
                requester
        );
        reviewPolicy.approveNext("approved by bob");
        RunResult resumed = executor.resume(suspended.runId(), registry.all(), registry, Map.of(), approver);

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(reviewPolicy.request().requestedBy()).isEqualTo(requester);
        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(action.executedPrincipal()).isEqualTo(requester);
        assertThat(traceRepository.findByRun(suspended.runId()))
                .filteredOn(event -> event.type() == TraceEventType.RUN_RESUMED)
                .singleElement()
                .satisfies(event -> assertThat(event.data())
                        .containsEntry("actedBy", "user:bob")
                        .containsEntry("actedByClientId", "approval-console"));
    }

    private ActionRegistry registry(Action action) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        registry.register(action);
        return registry;
    }

    private Action highRiskAction() {
        return new Action() {
            @Override
            public ActionId id() {
                return new ActionId("risk.approval.create");
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
                return ActionRiskLevel.HIGH;
            }

            @Override
            public boolean requiresHumanReview() {
                return true;
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

    private static final class CapturingHumanReviewPolicy implements HumanReviewPolicy {
        private final AtomicReference<HumanReviewRequest> request = new AtomicReference<>();
        private final AtomicReference<HumanReviewResult> nextResult =
                new AtomicReference<>(HumanReviewResult.pending("manual review required"));

        @Override
        public HumanReviewResult review(HumanReviewRequest request) {
            this.request.set(request);
            return nextResult.get();
        }

        HumanReviewRequest request() {
            return request.get();
        }

        void approveNext(String message) {
            nextResult.set(HumanReviewResult.approved("approver", message));
        }
    }

    private static final class PrincipalCapturingAction implements Action {
        private final AtomicReference<RunPrincipal> executedPrincipal = new AtomicReference<>();

        @Override
        public ActionId id() {
            return new ActionId("risk.principal.capture");
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
            return ActionRiskLevel.HIGH;
        }

        @Override
        public boolean requiresHumanReview() {
            return true;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            executedPrincipal.set(context.principal());
            return ActionResult.ok();
        }

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            return CompensationResult.noop();
        }

        RunPrincipal executedPrincipal() {
            return executedPrincipal.get();
        }
    }

    private static final class TokenMaskingPolicy implements DataMaskingPolicy {
        @Override
        public String maskText(String text) {
            return text == null ? "" : text.replace("Bearer test-token", "[MASKED]");
        }

        @Override
        public Map<String, String> maskData(Map<String, String> data) {
            return data.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().replace("Bearer test-token", "[MASKED]")
                    ));
        }
    }
}
