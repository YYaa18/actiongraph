package com.actiongraph.samples.ordercancellation;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.ordercancellation.interpretation.RuleBasedOrderCancellationGoalInterpreter;
import com.actiongraph.samples.ordercancellation.narration.OrderCancellationRunSummarizer;
import com.actiongraph.samples.ordercancellation.seed.OrderCancellationBlackboardSeeder;
import com.actiongraph.samples.ordercancellation.service.InMemoryCancellationPolicyService;
import com.actiongraph.samples.ordercancellation.service.InMemoryCancellationRequestService;
import com.actiongraph.samples.ordercancellation.service.InMemoryOperationsApprovalService;
import com.actiongraph.samples.ordercancellation.service.InMemoryOrderService;
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
import com.actiongraph.trace.InMemoryTraceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class OrderCancellationSampleApp {
    private OrderCancellationSampleApp() {
    }

    public static void main(String[] args) {
        SampleArgs sampleArgs = SampleArgs.parse(args);
        GoalInterpretation interpretation = new RuleBasedOrderCancellationGoalInterpreter().interpret(sampleArgs.input());
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
        seeders.register(new OrderCancellationBlackboardSeeder());
        seeders.seed(interpretation, blackboard);

        List<Action> actions = OrderCancellationActionFactory.actions(
                new InMemoryOrderService(sampleArgs.shipped()),
                new InMemoryCancellationPolicyService(),
                new InMemoryCancellationRequestService(),
                new InMemoryOperationsApprovalService()
        );
        DefaultActionRegistry registry = OrderCancellationActionFactory.registry(actions);
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
        System.out.println(new OrderCancellationRunSummarizer().summarize(result, blackboard));
        System.out.println("traceEvents=" + traceRepository.findByRun(result.runId()).size());
    }

    private record SampleArgs(String input, boolean shipped, HumanReviewPolicy humanReviewPolicy) {
        static SampleArgs parse(String[] args) {
            boolean shipped = false;
            HumanReviewPolicy humanReviewPolicy = new PendingHumanReviewPolicy();
            List<String> inputParts = new ArrayList<>();
            for (String arg : args) {
                if ("--approve-human-review".equals(arg)) {
                    humanReviewPolicy = new AutoApproveHumanReviewPolicy();
                } else if ("--shipped".equals(arg)) {
                    shipped = true;
                } else {
                    inputParts.add(arg);
                }
            }
            String input = inputParts.isEmpty()
                    ? "Cancel order O100"
                    : String.join(" ", inputParts);
            return new SampleArgs(input, shipped, humanReviewPolicy);
        }
    }
}
