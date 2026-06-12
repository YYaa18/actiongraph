package com.actiongraph.interpretation.annotation;

import com.actiongraph.action.annotation.BlackboardValue;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    void builtInEnumConversionTriesExactMatchBeforeIgnoreCase() {
        GoalBlackboardSeeder seeder = seeder("enum.case");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of("style", "snake_case")), blackboard);

        assertThat(blackboard.get(CaseStyle.class)).contains(CaseStyle.snake_case);
    }

    @Test
    void bindGoalParamsBindsRecordComponentsThroughBuiltInConversion() {
        GoalBlackboardSeeder seeder = seeder("product.bound-create");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of(
                "name", "键盘",
                "price", "299.00",
                "stock", "12"
        )), blackboard);

        assertThat(blackboard.get(BoundProductDraft.class)).hasValueSatisfying(draft -> {
            assertThat(draft.name()).isEqualTo("键盘");
            assertThat(draft.price()).isEqualByComparingTo("299.00");
            assertThat(draft.stock()).isEqualTo(12);
            assertThat(draft.status()).isNull();
        });
    }

    @Test
    void bindGoalParamsCanRequireAtLeastOneProvidedComponent() {
        GoalBlackboardSeeder seeder = seeder("product.patch");

        assertThatThrownBy(() -> seeder.seed(GoalParameters.empty(), new InMemoryBlackboard()))
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("At least one goal parameter must be provided")
                .hasMessageContaining("name")
                .hasMessageContaining("price");
    }

    @Test
    void bindGoalParamsAtLeastOneIgnoresRequiredReferenceComponents() {
        GoalBlackboardSeeder seeder = seederWithTypedLongConverter("product.update");

        assertThatThrownBy(() -> seeder.seed(GoalParameters.of(Map.of("productRef", "P42")), new InMemoryBlackboard()))
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("At least one goal parameter must be provided")
                .hasMessageContaining("name")
                .hasMessageContaining("price")
                .hasMessageNotContaining("productRef");
    }

    @Test
    void goalSchemaAtLeastOneIsEnforcedBySharedBinder() {
        GoalBlackboardSeeder seeder = seeder("product.schema-patch");

        assertThatThrownBy(() -> seeder.seed(GoalParameters.empty(), new InMemoryBlackboard()))
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("At least one goal parameter must be provided")
                .hasMessageContaining("name")
                .hasMessageContaining("price");
    }

    @Test
    void goalSchemaAtLeastOneUnknownComponentFailsAtFactoryCreation() {
        assertThatThrownBy(() -> AnnotatedGoalSeederFactory.seeders(new InvalidSchemaSeeders()))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("references unknown component")
                .hasMessageContaining("missing");
    }

    @Test
    void typedConverterIsUsedWhenNoExplicitConverterIsDeclared() {
        GoalBlackboardSeeder seeder = seederWithTypedLongConverter("product.typed-delete");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of("productRef", "P42")), blackboard);

        assertThat(blackboard.get(BlackboardKey.of(Long.class, "productId"))).contains(900L);
    }

    @Test
    void explicitConverterStillWinsOverTypedConverter() {
        GoalBlackboardSeeder seeder = seederWithTypedLongConverter("product.delete");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.of(Map.of("productRef", "P42")), blackboard);

        assertThat(blackboard.get(BlackboardKey.of(Long.class, "productId"))).contains(42L);
    }

    @Test
    void seederCanInheritSeedConditionsFromGoalDefinition() {
        GoalDefinition goal = new GoalDefinition(
                new GoalType("product.inherited"),
                "inherited",
                new Goal("inherited", Set.of(Condition.of("product:DONE"))),
                List.of(),
                Set.of(Condition.of("product:INHERITED"))
        );
        GoalBlackboardSeeder seeder = AnnotatedGoalSeederFactory
                .seeders(List.of(goal), new InheritedSeeders())
                .get(0);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        seeder.seed(GoalParameters.empty(), blackboard);

        assertThat(seeder.declaredSeedConditions()).contains(Set.of(Condition.of("product:INHERITED")));
        assertThat(blackboard.conditions()).containsExactly(Condition.of("product:INHERITED"));
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

    private GoalBlackboardSeeder seederWithTypedLongConverter(String goalType) {
        GoalValueConverterResolver resolver = new GoalValueConverterResolver() {
            @Override
            public GoalValueConverter<?> resolve(Class<? extends GoalValueConverter<?>> converterType) {
                return GoalValueConverterResolver.reflection().resolve(converterType);
            }

            @Override
            public Optional<GoalValueConverter<?>> resolveForType(Class<?> targetType) {
                if (targetType.equals(Long.class)) {
                    return Optional.of(new TypedProductReferenceConverter());
                }
                return Optional.empty();
            }
        };
        List<GoalBlackboardSeeder> seeders = AnnotatedGoalSeederFactory.seeders(resolver, new ProductSeeders());
        return seeders.stream()
                .filter(seeder -> seeder.goalType().value().equals(goalType))
                .findFirst()
                .orElseThrow();
    }

    private record ProductDraft(String name, BigDecimal price, int stock, ProductStatus status) {
    }

    private record BoundProductDraft(
            String name,
            BigDecimal price,
            int stock,
            @GoalParameter(required = false) ProductStatus status
    ) {
    }

    private record ProductPatch(
            @GoalParameter(required = false) String name,
            @GoalParameter(required = false) BigDecimal price
    ) {
    }

    @GoalSchema(atLeastOne = {"name", "price"})
    private record SchemaProductPatch(
            @GoalParameter(required = false) String name,
            @GoalParameter(required = false) BigDecimal price
    ) {
    }

    @GoalSchema(atLeastOne = "missing")
    private record InvalidProductPatch(
            @GoalParameter(required = false) String name
    ) {
    }

    private record ProductUpdate(
            @GoalParameter(name = "productRef") Long productId,
            @GoalParameter(required = false) String name,
            @GoalParameter(required = false) BigDecimal price
    ) {
    }

    private enum ProductStatus {
        ON_SALE,
        OFF_SALE
    }

    private enum CaseStyle {
        snake_case,
        UPPER_CASE
    }

    private static final class ProductSeeders {
        @ActionGraphGoalSeeder(value = "product.create", seedConditions = "product:CREATE_REQUESTED")
        ProductDraft create(
                @FromGoalParam("name") String name,
                @FromGoalParam("price") BigDecimal price,
                @FromGoalParam("stock") int stock,
                @FromGoalParam(value = "status", required = false, converter = ProductStatusConverter.class)
                ProductStatus status
        ) {
            return new ProductDraft(name, price, stock, status == null ? ProductStatus.ON_SALE : status);
        }

        @ActionGraphGoalSeeder(value = "product.delete", seedConditions = "product:DELETE_REQUESTED")
        SeedResult delete(
                @FromGoalParam(value = "productRef", converter = ProductReferenceConverter.class)
                Long productId
        ) {
            return SeedResult.builder()
                    .put(Long.class, "productId", productId)
                    .addCondition("product:REFERENCE_RESOLVED")
                    .build();
        }

        @ActionGraphGoalSeeder(value = "product.lookup", seedConditions = "product:LOOKUP_REQUESTED")
        @BlackboardValue("productId")
        Long lookup(GoalParameters parameters, Blackboard blackboard) {
            blackboard.addCondition(Condition.of("product:LOOKUP_USED_CONTEXT"));
            return Long.valueOf(parameters.get("id").orElseThrow());
        }

        @ActionGraphGoalSeeder("enum.case")
        CaseStyle enumCase(@FromGoalParam("style") CaseStyle style) {
            return style;
        }

        @ActionGraphGoalSeeder(value = "product.bound-create", seedConditions = "product:CREATE_REQUESTED")
        BoundProductDraft boundCreate(@BindGoalParams BoundProductDraft draft) {
            return draft;
        }

        @ActionGraphGoalSeeder(value = "product.patch", seedConditions = "product:PATCH_REQUESTED")
        ProductPatch patch(@BindGoalParams(atLeastOne = true) ProductPatch patch) {
            return patch;
        }

        @ActionGraphGoalSeeder(value = "product.schema-patch", seedConditions = "product:PATCH_REQUESTED")
        SchemaProductPatch schemaPatch(@BindGoalParams SchemaProductPatch patch) {
            return patch;
        }

        @ActionGraphGoalSeeder(value = "product.update", seedConditions = "product:UPDATE_REQUESTED")
        ProductUpdate update(@BindGoalParams(atLeastOne = true) ProductUpdate update) {
            return update;
        }

        @ActionGraphGoalSeeder(value = "product.typed-delete", seedConditions = "product:DELETE_REQUESTED")
        SeedResult typedDelete(@FromGoalParam("productRef") Long productId) {
            return SeedResult.builder()
                    .put(Long.class, "productId", productId)
                    .build();
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

    private static final class TypedProductReferenceConverter implements GoalValueConverter<Long> {
        @Override
        public Long convert(String rawValue, GoalParameterBindingContext context) {
            return 900L;
        }
    }

    private static final class DuplicateSeeders {
        @ActionGraphGoalSeeder("duplicate.goal")
        void first() {
        }

        @ActionGraphGoalSeeder("duplicate.goal")
        void second() {
        }
    }

    private static final class InheritedSeeders {
        @ActionGraphGoalSeeder("product.inherited")
        void seed() {
        }
    }

    private static final class InvalidSchemaSeeders {
        @ActionGraphGoalSeeder("product.invalid-schema")
        InvalidProductPatch seed(@BindGoalParams InvalidProductPatch patch) {
            return patch;
        }
    }
}
