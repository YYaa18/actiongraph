package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewResult;
import com.actiongraph.trace.InMemoryTraceRepository;
import org.junit.jupiter.api.Test;

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

        @Override
        public HumanReviewResult review(HumanReviewRequest request) {
            this.request.set(request);
            return HumanReviewResult.pending("manual review required");
        }

        HumanReviewRequest request() {
            return request.get();
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
