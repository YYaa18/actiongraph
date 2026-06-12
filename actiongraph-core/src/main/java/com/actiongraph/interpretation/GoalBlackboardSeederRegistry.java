package com.actiongraph.interpretation;

import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.interpretation.annotation.GoalParameterBinder;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GoalBlackboardSeederRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoalBlackboardSeederRegistry.class);
    private final Map<GoalType, GoalBlackboardSeeder> seeders = new LinkedHashMap<>();

    public void register(GoalBlackboardSeeder seeder) {
        Objects.requireNonNull(seeder, "seeder");
        GoalBlackboardSeeder previous = seeders.putIfAbsent(seeder.goalType(), seeder);
        if (previous != null) {
            throw new ActionGraphConfigurationException(
                    "Duplicate blackboard seeder for goal type: " + seeder.goalType());
        }
    }

    public Optional<GoalBlackboardSeeder> byGoalType(GoalType goalType) {
        return Optional.ofNullable(seeders.get(goalType));
    }

    @Experimental(
            since = "0.2.0",
            value = "Default seeder registration is experimental until no-parameter goal conventions settle."
    )
    public void registerDefaultSeeders(GoalCatalog catalog) {
        registerDefaultSeeders(catalog, GoalValueConverterResolver.reflection(), true);
    }

    @Experimental(
            since = "0.2.0",
            value = "Automatic schema seeding is experimental until DX3 pilots validate binding conventions."
    )
    public void registerDefaultSeeders(
            GoalCatalog catalog,
            GoalValueConverterResolver converterResolver,
            boolean autoSeedingEnabled
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(converterResolver, "converterResolver");
        if (!autoSeedingEnabled) {
            return;
        }
        GoalParameterBinder binder = new GoalParameterBinder(converterResolver);
        for (GoalDefinition definition : catalog.all()) {
            if (byGoalType(definition.type()).isPresent()) {
                LOGGER.debug("Skipping automatic seeder for {} because an explicit seeder is registered",
                        definition.type().value());
                continue;
            }
            if (!definition.schema().equals(Void.class)) {
                binder.validateRecordSchema(definition.schema());
                register(new SchemaGoalBlackboardSeeder(definition.type(), definition.schema(),
                        definition.seedConditions(), binder));
                continue;
            }
            if (!definition.seedConditions().isEmpty()
                    && definition.parameters().stream().noneMatch(GoalParameterDefinition::required)) {
                register(new SeedConditionSeeder(definition.type(), definition.seedConditions()));
            }
        }
    }

    public void seed(GoalInterpretation interpretation, Blackboard blackboard) {
        if (!interpretation.isReady()) {
            throw new ActionGraphInputException("Cannot seed blackboard from an incomplete interpretation");
        }
        GoalBlackboardSeeder seeder = byGoalType(interpretation.goalType())
                .orElseThrow(() -> new ActionGraphConfigurationException(
                        "No blackboard seeder registered for goal type: " + interpretation.goalType()));
        seeder.seed(interpretation.parameters(), blackboard);
    }

    private static final class SchemaGoalBlackboardSeeder implements GoalBlackboardSeeder {
        private final GoalType goalType;
        private final Class<?> schema;
        private final Set<Condition> seedConditions;
        private final GoalParameterBinder binder;

        private SchemaGoalBlackboardSeeder(
                GoalType goalType,
                Class<?> schema,
                Set<Condition> seedConditions,
                GoalParameterBinder binder
        ) {
            this.goalType = Objects.requireNonNull(goalType, "goalType");
            this.schema = Objects.requireNonNull(schema, "schema");
            this.seedConditions = Set.copyOf(Objects.requireNonNull(seedConditions, "seedConditions"));
            this.binder = Objects.requireNonNull(binder, "binder");
        }

        @Override
        public GoalType goalType() {
            return goalType;
        }

        @Override
        public void seed(GoalParameters parameters, Blackboard blackboard) {
            Object value = binder.bindRecord(schema, parameters, blackboard);
            blackboard.put(value);
            seedConditions.forEach(blackboard::addCondition);
        }

        @Override
        public Optional<Set<Condition>> declaredSeedConditions() {
            return Optional.of(seedConditions);
        }
    }

    private static final class SeedConditionSeeder implements GoalBlackboardSeeder {
        private final GoalType goalType;
        private final Set<Condition> seedConditions;

        private SeedConditionSeeder(GoalType goalType, Set<Condition> seedConditions) {
            this.goalType = Objects.requireNonNull(goalType, "goalType");
            this.seedConditions = Set.copyOf(Objects.requireNonNull(seedConditions, "seedConditions"));
        }

        @Override
        public GoalType goalType() {
            return goalType;
        }

        @Override
        public void seed(GoalParameters parameters, Blackboard blackboard) {
            seedConditions.forEach(blackboard::addCondition);
        }

        @Override
        public Optional<Set<Condition>> declaredSeedConditions() {
            return Optional.of(seedConditions);
        }
    }
}
