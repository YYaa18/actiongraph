package com.actiongraph.samples.renewal;

import com.actiongraph.ActionGraph;
import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalCatalog;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalInterpreterFactory;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.llm.LlmResponse;
import com.actiongraph.llm.LlmRequest;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
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
        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        RenewalGoalAnnotations.seeders().forEach(seeders::register);

        List<Action> actions = RenewalActionFactory.actions(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        AtomicReference<InMemoryBlackboard> blackboard = new AtomicReference<>();
        ActionGraph actionGraph = ActionGraph.builder()
                .goalCatalog(RenewalGoalCatalog.create())
                .seeders(seeders)
                .actionRegistry(registry)
                .executor(GoapExecutor.builder()
                        .humanReviewPolicy(new AutoApproveHumanReviewPolicy())
                        .traceRepository(new InMemoryTraceRepository())
                        .build())
                .goalInterpreter(interpreter)
                .blackboardFactory(() -> {
                    InMemoryBlackboard value = new InMemoryBlackboard();
                    blackboard.set(value);
                    return value;
                })
                .build();

        var result = actionGraph.chat("帮客户 C001 的合同弄个续约报价").run();

        assertThat(capturedRequest.get().systemPrompt()).contains("prepareRenewalQuote");
        assertThat(capturedRequest.get().systemPrompt()).contains("customerId (required)");
        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(blackboard.get().get(ApprovalRequest.class)).isPresent();
    }
}
