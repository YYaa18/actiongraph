package com.actiongraph.interpretation.annotation;

import com.actiongraph.action.annotation.BlackboardValue;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotatedGoalSeederFactoryTest {
    @Test
    void methodReturnSeedsObjectAndSeedConditions() {
        GoalBlackboardSeeder seeder = seeder("product.create");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of(
                "name", "无线鼠标",
                "price", "89.00",
                "stock", "35",
                "status", "上架"
        )), blackboard);

        assertThat(blackboard.conditions()).containsExactly(Condition.of("product:CREATE_REQUESTED"));
        assertThat(blackboard.get(ProductDraft.class)).hasValueSatisfying(product -> {
            assertThat(product.name()).isEqualTo("无线鼠标");
            assertThat(product.price()).isEqualByComparingTo("89.00");
            assertThat(product.stock()).isEqualTo(35);
            assertThat(product.status()).isEqualTo(ProductStatus.ON_SALE);
        });
    }

    @Test
    void seedResultCanWriteMultipleBlackboardValues() {
        GoalBlackboardSeeder seeder = seeder("product.delete");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of("productRef", "P42")), blackboard);

        assertThat(blackboard.get(BlackboardKey.of(Long.class, "productId"))).contains(42L);
        assertThat(blackboard.conditions())
                .containsExactlyInAnyOrder(
                        Condition.of("product:REFERENCE_RESOLVED"),
                        Condition.of("product:DELETE_REQUESTED")
                );
    }

    @Test
    void methodCanUseGoalParametersBlackboardAndKeyedReturnValue() {
        GoalBlackboardSeeder seeder = seeder("product.lookup");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of("id", "7")), blackboard);

        assertThat(blackboard.get(BlackboardKey.of(Long.class, "productId"))).contains(7L);
        assertThat(blackboard.conditions())
                .containsExactlyInAnyOrder(
                        Condition.of("product:LOOKUP_USED_CONTEXT"),
                        Condition.of("product:LOOKUP_REQUESTED")
                );
    }

    @Test
    void missingRequiredGoalParameterFailsFast() {
        GoalBlackboardSeeder seeder = seeder("product.create");

        assertThatThrownBy(() -> seeder.seed(GoalParameters.empty(), new InMemoryBlackboard()))
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("Missing goal parameter: name");
    }

    @Test
    void duplicateAnnotatedSeederGoalTypeFailsFast() {
        assertThatThrownBy(() -> AnnotatedGoalSeederFactory.seeders(new DuplicateSeeders()))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("Duplicate annotated goal seeder");
    }

    private GoalBlackboardSeeder seeder(String goalType) {
        List<GoalBlackboardSeeder> seeders = AnnotatedGoalSeederFactory.seeders(new ProductSeeders());
        return seeders.stream()
                .filter(seeder -> seeder.goalType().value().equals(goalType))
                .findFirst()
                .orElseThrow();
    }

    private record ProductDraft(String name, BigDecimal price, int stock, ProductStatus status) {
    }

    private enum ProductStatus {
        ON_SALE,
        OFF_SALE
    }

    private static final class ProductSeeders {
        @ActionGraphGoalSeeder(goal = "product.create", seedConditions = "product:CREATE_REQUESTED")
        ProductDraft create(
                @GoalParam("name") String name,
                @GoalParam("price") BigDecimal price,
                @GoalParam("stock") int stock,
                @GoalParam(value = "status", required = false, converter = ProductStatusConverter.class)
                ProductStatus status
        ) {
            return new ProductDraft(name, price, stock, status == null ? ProductStatus.ON_SALE : status);
        }

        @ActionGraphGoalSeeder(goal = "product.delete", seedConditions = "product:DELETE_REQUESTED")
        SeedResult delete(
                @GoalParam(value = "productRef", converter = ProductReferenceConverter.class)
                Long productId
        ) {
            return SeedResult.builder()
                    .put(Long.class, "productId", productId)
                    .addCondition("product:REFERENCE_RESOLVED")
                    .build();
        }

        @ActionGraphGoalSeeder(goal = "product.lookup", seedConditions = "product:LOOKUP_REQUESTED")
        @BlackboardValue("productId")
        Long lookup(GoalParameters parameters, Blackboard blackboard) {
            blackboard.addCondition(Condition.of("product:LOOKUP_USED_CONTEXT"));
            return Long.valueOf(parameters.get("id").orElseThrow());
        }
    }

    private static final class ProductStatusConverter implements GoalValueConverter<ProductStatus> {
        @Override
        public ProductStatus convert(String rawValue, GoalParameterBindingContext context) {
            return switch (rawValue) {
                case "上架", "ON_SALE" -> ProductStatus.ON_SALE;
                case "下架", "OFF_SALE" -> ProductStatus.OFF_SALE;
                default -> throw new ActionGraphInputException("无法识别商品状态：" + rawValue);
            };
        }
    }

    private static final class ProductReferenceConverter implements GoalValueConverter<Long> {
        @Override
        public Long convert(String rawValue, GoalParameterBindingContext context) {
            return Long.valueOf(rawValue.replace("P", ""));
        }
    }

    private static final class DuplicateSeeders {
        @ActionGraphGoalSeeder("duplicate.goal")
        void first() {
        }

        @ActionGraphGoalSeeder(goalType = "duplicate.goal")
        void second() {
        }
    }
}
