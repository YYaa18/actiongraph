package com.actiongraph.samples.renewal;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalInterpreterFactory;
import com.actiongraph.samples.renewal.narration.RenewalPlanExplainer;
import com.actiongraph.samples.renewal.narration.RenewalRunSummarizer;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Plan;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.DenyingHumanReviewPolicy;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.trace.InMemoryTraceRepository;

import java.util.List;

public final class RenewalQuoteSampleApp {
    private RenewalQuoteSampleApp() {
    }

    public static void main(String[] args) {
        SampleArgs sampleArgs = SampleArgs.parse(args);
        String input = sampleArgs.input();

        GoalInterpreter interpreter = RenewalGoalInterpreterFactory.createDefault();
        GoalInterpretation interpretation = interpreter.interpret(input);
        System.out.println("input=" + input);
        System.out.println("goalType=" + interpretation.goalType());
        System.out.println("parameters=" + interpretation.parameters().values());
        if (!interpretation.isReady()) {
            System.out.println("missingFields=" + interpretation.missingFields());
            interpretation.clarificationQuestion()
                    .ifPresent(question -> System.out.println("clarificationQuestion=" + question.text()));
            return;
        }

        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        RenewalContribution contribution = new RenewalContribution(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );

        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        contribution.seeders().forEach(seeders::register);
        seeders.seed(interpretation, blackboard);

        List<Action> actions = contribution.actions();
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        GoapPlanner planner = new GoapPlanner();

        Plan plan = planner.plan(
                interpretation.goal().orElseThrow(),
                blackboard.conditions(),
                actions
        ).orElseThrow(() -> new IllegalStateException("No plan found before execution"));
        System.out.println(new RenewalPlanExplainer().explain(plan));

        GoapExecutor executor = new GoapExecutor(
                planner,
                new DefaultPolicyGuard(),
                sampleArgs.humanReviewPolicy(),
                traceRepository
        );

        RunResult result = executor.run(interpretation.goal().orElseThrow(), blackboard, actions, registry);
        System.out.println("status=" + result.status());
        System.out.println("executedActions=" + result.executedActions());
        System.out.println("finalConditions=" + result.finalState());
        System.out.println(new RenewalRunSummarizer().summarize(result, blackboard));
        System.out.println("traceEvents=" + traceRepository.findByRun(result.runId()).size());
    }

    private record SampleArgs(String input, HumanReviewPolicy humanReviewPolicy) {
        static SampleArgs parse(String[] args) {
            HumanReviewPolicy humanReviewPolicy = new PendingHumanReviewPolicy();
            List<String> inputParts = new java.util.ArrayList<>();
            for (String arg : args) {
                if ("--approve-human-review".equals(arg)) {
                    humanReviewPolicy = new AutoApproveHumanReviewPolicy();
                } else if ("--deny-human-review".equals(arg)) {
                    humanReviewPolicy = new DenyingHumanReviewPolicy();
                } else {
                    inputParts.add(arg);
                }
            }
            String input = inputParts.isEmpty()
                    ? "Prepare renewal quote for C001"
                    : String.join(" ", inputParts);
            return new SampleArgs(input, humanReviewPolicy);
        }
    }
}
