package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.RenewalActionFactory;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.RenewalGoals;
import com.actiongraph.samples.renewal.actions.QuoteDraftCreateAction;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorTest {
    @Test
    void happyPathCompletesAndReplansAfterEachStep() {
        Fixture fixture = fixture(true, false);

        RunResult result = fixture.run();

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(fixture.blackboard().get(ApprovalRequest.class)).isPresent();
        assertThat(actionIds(result)).containsExactly(
                "contract.current.query",
                "customer.profile.query",
                "renewal.eligibility.check",
                "quote.draft.create",
                "sales.approval.request"
        );
        assertThat(events(fixture, result, TraceEventType.PLAN_GENERATED))
                .hasSize(result.executedActions().size());
        assertThat(events(fixture, result, TraceEventType.HUMAN_REVIEW_REQUESTED))
                .extracting(TraceEvent::actionId)
                .containsExactly("sales.approval.request");
    }

    @Test
    void runtimeGuardFailureHaltsWithoutExecutingGuardedAction() {
        Fixture fixture = fixture(false, false);

        RunResult result = fixture.run();

        assertThat(result.status()).isEqualTo(RunStatus.HALTED_UNREACHABLE);
        assertThat(result.message()).contains(QuoteDraftCreateAction.ID.value());
        assertThat(fixture.quoteService().drafts()).isEmpty();
        assertThat(actionIds(result)).doesNotContain("quote.draft.create", "sales.approval.request");
        assertThat(events(fixture, result, TraceEventType.RUNTIME_GUARD_FAILED))
                .extracting(TraceEvent::actionId)
                .containsExactly("quote.draft.create");
    }

    @Test
    void executeFailureTriggersReverseCompensation() {
        Fixture fixture = fixture(true, true);

        RunResult result = fixture.run();

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_COMPENSATED);
        assertThat(actionIds(result)).contains("quote.draft.create");
        assertThat(actionIds(result)).doesNotContain("sales.approval.request");
        assertThat(fixture.quoteService().voidedQuoteIds()).containsExactly("QUOTE-1");

        List<TraceEvent> compensated = events(fixture, result, TraceEventType.COMPENSATED);
        assertThat(compensated).isNotEmpty();
        assertThat(compensated.getFirst().actionId()).isEqualTo("quote.draft.create");
    }

    private Fixture fixture(boolean eligible, boolean approvalFails) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new CustomerId("C001"));
        blackboard.addCondition(RenewalConditions.CUSTOMER_ID_PRESENT);

        InMemoryQuoteService quoteService = new InMemoryQuoteService();
        InMemoryApprovalService approvalService = new InMemoryApprovalService(approvalFails);
        List<Action> actions = RenewalActionFactory.actions(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(eligible, eligible ? "near expiry" : "blocked"),
                quoteService,
                approvalService
        );
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                traceRepository
        );
        return new Fixture(blackboard, actions, registry, quoteService, traceRepository, executor);
    }

    private List<String> actionIds(RunResult result) {
        return result.executedActions().stream()
                .map(ActionId::value)
                .toList();
    }

    private List<TraceEvent> events(Fixture fixture, RunResult result, TraceEventType type) {
        return fixture.traceRepository().findByRun(result.runId()).stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    private record Fixture(
            InMemoryBlackboard blackboard,
            List<Action> actions,
            DefaultActionRegistry registry,
            InMemoryQuoteService quoteService,
            InMemoryTraceRepository traceRepository,
            GoapExecutor executor
    ) {
        RunResult run() {
            return executor.run(RenewalGoals.prepareRenewalQuote(), blackboard, actions, registry);
        }
    }
}
