package com.actiongraph.samples.renewal;

import com.actiongraph.ActionGraph;
import com.actiongraph.ChatResult;
import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalCatalog;
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
import java.util.concurrent.atomic.AtomicReference;

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

        RenewalContribution contribution = new RenewalContribution(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );

        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        contribution.seeders().forEach(seeders::register);
        InMemoryBlackboard previewBlackboard = new InMemoryBlackboard();
        seeders.seed(interpretation, previewBlackboard);

        List<Action> actions = contribution.actions();
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        GoapPlanner planner = new GoapPlanner();

        Plan plan = planner.plan(
                interpretation.goal().orElseThrow(),
                previewBlackboard.conditions(),
                actions
        ).orElseThrow(() -> new IllegalStateException("No plan found before execution"));
        System.out.println(new RenewalPlanExplainer().explain(plan));

        GoapExecutor executor = new GoapExecutor(
                planner,
                new DefaultPolicyGuard(),
                sampleArgs.humanReviewPolicy(),
                traceRepository
        );

        AtomicReference<InMemoryBlackboard> runBlackboard = new AtomicReference<>();
        ActionGraph actionGraph = ActionGraph.builder()
                .goalCatalog(RenewalGoalCatalog.create())
                .seeders(seeders)
                .actionRegistry(registry)
                .executor(executor)
                .goalInterpreter(interpreter)
                .blackboardFactory(() -> {
                    InMemoryBlackboard blackboard = new InMemoryBlackboard();
                    runBlackboard.set(blackboard);
                    return blackboard;
                })
                .build();

        ChatResult chat = actionGraph.chat(input);
        RunResult result = chat.run();
        InMemoryBlackboard blackboard = runBlackboard.get();
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
