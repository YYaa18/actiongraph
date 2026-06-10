package com.actiongraph.samples.renewal.seed;

import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalTypes;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.Blackboard;

public final class RenewalQuoteBlackboardSeeder implements GoalBlackboardSeeder {
    public static final String CUSTOMER_ID_PARAMETER = "customerId";

    @Override
    public GoalType goalType() {
        return RenewalGoalTypes.PREPARE_RENEWAL_QUOTE;
    }

    @Override
    public void seed(GoalParameters parameters, Blackboard blackboard) {
        String customerId = parameters.get(CUSTOMER_ID_PARAMETER)
                .orElseThrow(() -> new MissingGoalParameterException(CUSTOMER_ID_PARAMETER));
        blackboard.put(new CustomerId(customerId));
        blackboard.addCondition(RenewalConditions.CUSTOMER_ID_PRESENT);
    }
}
