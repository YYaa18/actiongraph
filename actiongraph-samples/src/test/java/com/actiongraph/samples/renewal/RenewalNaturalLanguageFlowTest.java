package com.actiongraph.samples.renewal;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalInterpreterFactory;
import com.actiongraph.samples.renewal.seed.RenewalQuoteBlackboardSeeder;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.llm.LlmResponse;
import com.actiongraph.llm.LlmRequest;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.trace.InMemoryTraceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalNaturalLanguageFlowTest {
    @Test
    void naturalLanguageToCompletedRenewalQuoteViaFakeLlm() {
        AtomicReference<LlmRequest> capturedRequest = new AtomicReference<>();
        var interpreter = RenewalGoalInterpreterFactory.createWithOptionalLlm(Optional.of(request -> {
            capturedRequest.set(request);
            return new LlmResponse("""
                {
                  "goalType": "prepareRenewalQuote",
                  "parameters": {"customerId": "C001"},
                  "missingFields": [],
                  "clarificationQuestion": null
                }
                """);
        }));
        var interpretation = interpreter.interpret("帮客户 C001 的合同弄个续约报价");

        assertThat(capturedRequest.get().systemPrompt()).contains("prepareRenewalQuote");
        assertThat(capturedRequest.get().systemPrompt()).contains("customerId (required)");

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        seeders.register(new RenewalQuoteBlackboardSeeder());
        seeders.seed(interpretation, blackboard);

        List<Action> actions = RenewalActionFactory.actions(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                new InMemoryTraceRepository()
        );

        var result = executor.run(interpretation.goal().orElseThrow(), blackboard, actions, registry);

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(blackboard.get(ApprovalRequest.class)).isPresent();
    }
}
