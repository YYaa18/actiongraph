package com.actiongraph.interpretation.annotation;

import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.planning.Condition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotatedGoalFactoryTest {
    @Test
    void classAnnotationDerivesSchemaFromRecordComponents() {
        List<GoalDefinition> definitions = AnnotatedGoalFactory.definitions(RenewalQuoteGoal.class);

        assertThat(definitions).singleElement().satisfies(definition -> {
            assertThat(definition.type().value()).isEqualTo("renewal.prepare");
            assertThat(definition.description()).isEqualTo("Prepare renewal quote");
            assertThat(definition.goal().name()).isEqualTo("prepareRenewalQuote");
            assertThat(definition.goal().targetConditions())
                    .containsExactly(Condition.of("renewal:SALES_APPROVAL_REQUESTED"));
            assertThat(definition.seedConditions()).containsExactly(Condition.of("renewal:CUSTOMER_ID_PRESENT"));
            assertThat(definition.parameters()).singleElement().satisfies(parameter -> {
                assertThat(parameter.name()).isEqualTo("customerId");
                assertThat(parameter.description()).isEqualTo("Customer identifier");
                assertThat(parameter.required()).isTrue();
                assertThat(parameter.example()).contains("C001");
            });
        });
    }

    @Test
    void methodAnnotationCanFlattenRecordSchemaParameter() {
        List<GoalDefinition> definitions = AnnotatedGoalFactory.definitions(new GoalMethods());

        assertThat(definitions)
                .filteredOn(definition -> definition.type().value().equals("claim.precheck"))
                .singleElement()
                .satisfies(definition -> assertThat(definition.parameters())
                        .extracting(parameter -> parameter.name())
                        .containsExactly("claimId", "includeDocuments"));
    }

    @Test
    void methodAnnotationCanUseExplicitParameterNames() {
        List<GoalDefinition> definitions = AnnotatedGoalFactory.definitions(new GoalMethods());

        assertThat(definitions)
                .filteredOn(definition -> definition.type().value().equals("order.cancel"))
                .singleElement()
                .satisfies(definition -> {
                    assertThat(definition.parameters()).singleElement().satisfies(parameter -> {
                        assertThat(parameter.name()).isEqualTo("orderId");
                        assertThat(parameter.description()).isEqualTo("Order id");
                        assertThat(parameter.required()).isTrue();
                    });
                });
    }

    @Test
    void methodAnnotationCanUseExplicitSchemaClass() {
        List<GoalDefinition> definitions = AnnotatedGoalFactory.definitions(new GoalMethods());

        assertThat(definitions)
                .filteredOn(definition -> definition.type().value().equals("legacy.review"))
                .singleElement()
                .satisfies(definition -> assertThat(definition.parameters())
                        .extracting(parameter -> parameter.name())
                        .containsExactly("amount", "customerId"));
    }

    @Test
    void duplicateAnnotatedGoalTypeFailsFast() {
        assertThatThrownBy(() -> AnnotatedGoalFactory.definitions(DuplicateOne.class, DuplicateTwo.class))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("Duplicate annotated goal type");
    }

    @Test
    void unnamedMethodParameterRequiresAnnotationOrRecordSchema() {
        assertThatThrownBy(() -> AnnotatedGoalFactory.definitions(new InvalidGoalMethods()))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("@GoalParameter(name=...)");
    }

    @ActionGraphGoal(
            type = "renewal.prepare",
            description = "Prepare renewal quote",
            name = "prepareRenewalQuote",
            targetConditions = "renewal:SALES_APPROVAL_REQUESTED",
            seedConditions = "renewal:CUSTOMER_ID_PRESENT"
    )
    private record RenewalQuoteGoal(
            @GoalParameter(description = "Customer identifier", example = "C001")
            String customerId
    ) {
    }

    private record ClaimPrecheckGoal(
            @GoalParameter(description = "Claim id", example = "CLM100")
            String claimId,
            @GoalParameter(description = "Whether to include documents", required = false, example = "true")
        boolean includeDocuments
    ) {
    }

    private static final class LegacyReviewSchema {
        @GoalParameter(description = "Customer id", example = "C001")
        private String customerId;

        @GoalParameter(description = "Review amount", required = false, example = "1000.00")
        private String amount;

        @SuppressWarnings("unused")
        private static String ignoredStaticField;
    }

    private static final class GoalMethods {
        @ActionGraphGoal(
                type = "claim.precheck",
                description = "Run claim precheck",
                targetConditions = "claims:PRECHECKED",
                seedConditions = "claims:CLAIM_ID_PRESENT"
        )
        void claimPrecheck(ClaimPrecheckGoal ignored) {
        }

        @ActionGraphGoal(
                type = "order.cancel",
                description = "Cancel an order",
                targetConditions = "order:CANCELLED"
        )
        void cancelOrder(@GoalParameter(name = "orderId", description = "Order id", example = "O-1") String ignored) {
        }

        @ActionGraphGoal(
                type = "legacy.review",
                description = "Review a legacy request",
                targetConditions = "legacy:REVIEWED",
                schema = LegacyReviewSchema.class
        )
        void legacyReview() {
        }
    }

    @ActionGraphGoal(type = "duplicate.goal", targetConditions = "demo:DONE")
    private record DuplicateOne(String id) {
    }

    @ActionGraphGoal(type = "duplicate.goal", targetConditions = "demo:DONE")
    private record DuplicateTwo(String id) {
    }

    private static final class InvalidGoalMethods {
        @ActionGraphGoal(type = "invalid.goal", targetConditions = "demo:DONE")
        void invalid(String missingName) {
        }
    }
}
