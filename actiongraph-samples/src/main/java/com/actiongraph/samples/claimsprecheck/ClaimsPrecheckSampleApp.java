package com.actiongraph.samples.claimsprecheck;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Plan;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.samples.claimsprecheck.interpretation.RuleBasedClaimsPrecheckGoalInterpreter;
import com.actiongraph.samples.claimsprecheck.narration.ClaimsPrecheckRunSummarizer;
import com.actiongraph.samples.claimsprecheck.seed.ClaimsPrecheckBlackboardSeeder;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimApprovalService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimDocumentService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimPrecheckService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryClaimService;
import com.actiongraph.samples.claimsprecheck.service.InMemoryPayoutDraftService;
import com.actiongraph.trace.InMemoryTraceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ClaimsPrecheckSampleApp {
    private ClaimsPrecheckSampleApp() {
    }

    public static void main(String[] args) {
        SampleArgs sampleArgs = SampleArgs.parse(args);
        GoalInterpretation interpretation = new RuleBasedClaimsPrecheckGoalInterpreter().interpret(sampleArgs.input());
        System.out.println("input=" + sampleArgs.input());
        System.out.println("goalType=" + interpretation.goalType());
        System.out.println("parameters=" + interpretation.parameters().values());
        if (!interpretation.isReady()) {
            System.out.println("missingFields=" + interpretation.missingFields());
            interpretation.clarificationQuestion()
                    .ifPresent(question -> System.out.println("clarificationQuestion=" + question.text()));
            return;
        }

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        seeders.register(new ClaimsPrecheckBlackboardSeeder());
        seeders.seed(interpretation, blackboard);

        InMemoryPayoutDraftService draftService = new InMemoryPayoutDraftService();
        InMemoryClaimApprovalService approvalService = new InMemoryClaimApprovalService();
        List<Action> actions = ClaimsPrecheckActionFactory.actions(
                new InMemoryClaimService(),
                new InMemoryClaimDocumentService(sampleArgs.missingInvoice()),
                new InMemoryClaimPrecheckService(),
                draftService,
                approvalService
        );
        DefaultActionRegistry registry = ClaimsPrecheckActionFactory.registry(actions);
        GoapPlanner planner = new GoapPlanner();
        Plan plan = planner.plan(interpretation.goal().orElseThrow(), blackboard.conditions(), actions)
                .orElseThrow(() -> new IllegalStateException("No plan found before execution"));
        System.out.println("Plan: " + plan.steps().stream()
                .map(step -> step.actionId().value())
                .collect(Collectors.joining(" -> ")));

        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        RunResult result = new GoapExecutor(
                planner,
                new DefaultPolicyGuard(),
                sampleArgs.humanReviewPolicy(),
                traceRepository
        ).run(interpretation.goal().orElseThrow(), blackboard, actions, registry);

        System.out.println("status=" + result.status());
        System.out.println("executedActions=" + result.executedActions());
        System.out.println("finalConditions=" + result.finalState());
        System.out.println(new ClaimsPrecheckRunSummarizer().summarize(result, blackboard));
        System.out.println("traceEvents=" + traceRepository.findByRun(result.runId()).size());
    }

    private record SampleArgs(String input, boolean missingInvoice, HumanReviewPolicy humanReviewPolicy) {
        static SampleArgs parse(String[] args) {
            boolean missingInvoice = false;
            HumanReviewPolicy humanReviewPolicy = new PendingHumanReviewPolicy();
            List<String> inputParts = new ArrayList<>();
            for (String arg : args) {
                if ("--approve-human-review".equals(arg)) {
                    humanReviewPolicy = new AutoApproveHumanReviewPolicy();
                } else if ("--missing-invoice".equals(arg)) {
                    missingInvoice = true;
                } else {
                    inputParts.add(arg);
                }
            }
            String input = inputParts.isEmpty()
                    ? "Prepare payout application for claim CLM100"
                    : String.join(" ", inputParts);
            return new SampleArgs(input, missingInvoice, humanReviewPolicy);
        }
    }
}
