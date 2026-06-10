package com.actiongraph.samples.ordercancellation.seed;

import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.samples.ordercancellation.domain.OrderId;
import com.actiongraph.samples.ordercancellation.interpretation.OrderCancellationGoalTypes;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.Blackboard;

public final class OrderCancellationBlackboardSeeder implements GoalBlackboardSeeder {
    public static final String ORDER_ID_PARAMETER = "orderId";

    @Override
    public GoalType goalType() {
        return OrderCancellationGoalTypes.REQUEST_ORDER_CANCELLATION;
    }

    @Override
    public void seed(GoalParameters parameters, Blackboard blackboard) {
        String orderId = parameters.get(ORDER_ID_PARAMETER)
                .orElseThrow(() -> new MissingGoalParameterException(ORDER_ID_PARAMETER));
        blackboard.put(new OrderId(orderId));
        blackboard.addCondition(OrderCancellationConditions.ORDER_ID_PRESENT);
    }
}
