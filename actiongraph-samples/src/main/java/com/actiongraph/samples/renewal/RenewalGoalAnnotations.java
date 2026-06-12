package com.actiongraph.samples.renewal;

import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.annotation.ActionGraphGoal;
import com.actiongraph.interpretation.annotation.ActionGraphGoalSeeder;
import com.actiongraph.interpretation.annotation.AnnotatedGoalFactory;
import com.actiongraph.interpretation.annotation.AnnotatedGoalSeederFactory;
import com.actiongraph.interpretation.annotation.FromGoalParam;
import com.actiongraph.interpretation.annotation.GoalParameter;
import com.actiongraph.planning.Goal;
import com.actiongraph.samples.renewal.domain.CustomerId;

import java.util.List;

public final class RenewalGoalAnnotations {
    public static final String CUSTOMER_ID_PARAMETER = "customerId";

    private RenewalGoalAnnotations() {
    }

    public static List<GoalDefinition> goals() {
        return AnnotatedGoalFactory.definitions(RenewalGoalAnnotations.class);
    }

    public static Goal prepareRenewalQuoteGoal() {
        return goals().stream()
                .filter(goal -> goal.type().value().equals("prepareRenewalQuote"))
                .findFirst()
                .orElseThrow()
                .goal();
    }

    public static List<GoalBlackboardSeeder> seeders() {
        return AnnotatedGoalSeederFactory.seeders(RenewalGoalAnnotations.class);
    }

    @ActionGraphGoal(
            type = "prepareRenewalQuote",
            name = "prepareRenewalQuote",
            description = "Prepare a renewal quote for an existing customer and request sales approval.",
            targetConditions = "renewal:SALES_APPROVAL_REQUESTED",
            seedConditions = "renewal:CUSTOMER_ID_PRESENT"
    )
    static void prepareRenewalQuote(
            @GoalParameter(
                    name = CUSTOMER_ID_PARAMETER,
                    description = "Customer identifier. Use canonical IDs such as C001.",
                    example = "C001"
            )
            String ignored
    ) {
    }

    @ActionGraphGoalSeeder(value = "prepareRenewalQuote", seedConditions = "renewal:CUSTOMER_ID_PRESENT")
    static CustomerId seedPrepareRenewalQuote(@FromGoalParam(CUSTOMER_ID_PARAMETER) String customerId) {
        return new CustomerId(customerId);
    }
}
