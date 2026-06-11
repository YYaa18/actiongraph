package demo;

import com.actiongraph.action.ActionRegistry;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.Executor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
final class CancellationRunService {
    private static final Condition ORDER_ID_PRESENT =
            Condition.of("order-cancellation", "ORDER_ID_PRESENT");
    private static final Condition APPROVAL_REQUESTED =
            Condition.of("order-cancellation", "OPERATIONS_APPROVAL_REQUESTED");

    private final Executor executor;
    private final ActionRegistry registry;

    CancellationRunService(Executor executor, ActionRegistry registry) {
        this.executor = executor;
        this.registry = registry;
    }

    RunResult requestCancellation(String orderId) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new OrderId(orderId));
        blackboard.addCondition(ORDER_ID_PRESENT);

        return executor.run(
                new Goal("requestOrderCancellation", Set.of(APPROVAL_REQUESTED)),
                blackboard,
                registry.all(),
                registry
        );
    }
}
