package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.governance.RegexMaskingPolicy;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDataMaskingTest {
    private static final Condition CUSTOMER_PRESENT = Condition.of("mask-test:CUSTOMER_PRESENT");
    private static final Condition PREPARED = Condition.of("mask-test:PREPARED");
    private static final Condition APPROVED = Condition.of("mask-test:APPROVED");

    private static final SensitiveCustomer SENSITIVE_CUSTOMER = new SensitiveCustomer(
            "110101199001011234",
            "6228481234561234",
            "13812345678",
            "zhangsan@bank.com"
    );

    @Test
    void masksTraceAndHumanReviewPreviewButKeepsSuspendedSnapshotLossless() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRepository traceRepository = new JdbcTraceRepository(dataSource);
        JdbcSuspendedRunRepository suspendedRunRepository = new JdbcSuspendedRunRepository(dataSource);
        JdbcHumanReviewRepository humanReviewRepository = new JdbcHumanReviewRepository(dataSource);
        ActionRegistry registry = registry(new PrepareSensitiveAction(), new HighRiskApprovalAction());
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(SENSITIVE_CUSTOMER);
        blackboard.addCondition(CUSTOMER_PRESENT);

        GoapExecutor executor = GoapExecutor.builder()
                .planner(new GoapPlanner())
                .policyGuard(new DefaultPolicyGuard())
                .humanReviewPolicy(new RepositoryBackedHumanReviewPolicy(humanReviewRepository))
                .traceRepository(traceRepository)
                .suspendedRunRepository(suspendedRunRepository)
                .maskingPolicy(RegexMaskingPolicy.financialDefaults())
                .build();

        var result = executor.run(
                new Goal("maskSensitiveData", Set.of(APPROVED)),
                blackboard,
                registry.all(),
                registry
        );

        assertThat(result.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        String traceText = traceRepository.findByRun(result.runId()).stream()
                .flatMap(JdbcDataMaskingTest::traceText)
                .collect(Collectors.joining("\n"));
        assertThat(traceText)
                .contains("110101********1234")
                .contains("622848******1234")
                .contains("138****5678")
                .contains("z***@bank.com");
        assertNoRawSensitiveValues(traceText);

        HumanReviewTask reviewTask = humanReviewRepository.findPending().getFirst();
        String previewText = String.join("\n", reviewTask.blackboardPreview().values());
        assertThat(previewText)
                .contains("110101********1234")
                .contains("622848******1234")
                .contains("138****5678")
                .contains("z***@bank.com");
        assertNoRawSensitiveValues(previewText);

        var restored = suspendedRunRepository.findByRunId(result.runId()).orElseThrow();
        assertThat(restored.blackboard().get(SensitiveCustomer.class)).contains(SENSITIVE_CUSTOMER);
    }

    private static Stream<String> traceText(TraceEvent event) {
        return Stream.concat(Stream.of(event.detail()), event.data().values().stream());
    }

    private static void assertNoRawSensitiveValues(String text) {
        assertThat(text)
                .doesNotContain(SENSITIVE_CUSTOMER.idCard())
                .doesNotContain(SENSITIVE_CUSTOMER.cardNo())
                .doesNotContain(SENSITIVE_CUSTOMER.mobile())
                .doesNotContain(SENSITIVE_CUSTOMER.email());
    }

    private static ActionRegistry registry(Action... actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        for (Action action : actions) {
            registry.register(action);
        }
        return registry;
    }

    public record SensitiveCustomer(String idCard, String cardNo, String mobile, String email) {
    }

    private static final class PrepareSensitiveAction implements Action {
        @Override
        public ActionId id() {
            return new ActionId("mask-test.prepare");
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of(SensitiveCustomer.class);
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(CUSTOMER_PRESENT);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(PREPARED);
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
            SensitiveCustomer customer = context.blackboard().get(SensitiveCustomer.class).orElseThrow();
            return new ActionResult(true, "prepared " + customer, List.of());
        }
    }

    private static final class HighRiskApprovalAction implements Action {
        @Override
        public ActionId id() {
            return new ActionId("mask-test.approve");
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of(SensitiveCustomer.class);
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(PREPARED);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(APPROVED);
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
    }
}
