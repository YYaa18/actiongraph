package com.actiongraph.spring;

import com.actiongraph.ActionGraph;
import com.actiongraph.action.Action;
import com.actiongraph.action.ActionExecutionPolicy;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.action.annotation.AnnotatedActionFactory;
import com.actiongraph.api.Experimental;
import com.actiongraph.contribution.ActionGraphContribution;
import com.actiongraph.durability.RunRecoverer;
import com.actiongraph.events.EventApplier;
import com.actiongraph.events.EventWaitSweeper;
import com.actiongraph.events.ExternalEventGateway;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.fingerprint.ActionGraphFingerprints;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.annotation.AnnotatedGoalFactory;
import com.actiongraph.interpretation.annotation.AnnotatedGoalSeederFactory;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinition;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinitionFactory;
import com.actiongraph.interpretation.config.ConfiguredGoalParameter;
import com.actiongraph.llm.DeepSeekChatClient;
import com.actiongraph.llm.LlmClient;
import com.actiongraph.llm.OpenAiCompatibleChatClient;
import com.actiongraph.observability.NoopObservationSink;
import com.actiongraph.observability.ObservationSink;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Planner;
import com.actiongraph.policy.DataMaskingPolicy;
import com.actiongraph.policy.DefaultPermissionPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.ExecutionPolicyGuard;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.NoopMaskingPolicy;
import com.actiongraph.policy.NoopReviewAttributeContributor;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.policy.ReviewAttributeContributor;
import com.actiongraph.policy.RolePermissionPolicy;
import com.actiongraph.runtime.Executor;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.RunPrincipalResolver;
import com.actiongraph.spring.security.SpringSecurityRunPrincipalResolver;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceRepository;
import com.actiongraph.validation.ActionGraphValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.SmartLifecycle;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoConfiguration
@EnableConfigurationProperties(ActionGraphProperties.class)
public class ActionGraphAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionGraphAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public Planner actionGraphPlanner(ActionGraphProperties properties) {
        return new GoapPlanner(
                properties.getPlanner().getMaxDepth(),
                properties.getPlanner().getMaxExpansions()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceRepository actionGraphTraceRepository() {
        return new InMemoryTraceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public SuspendedRunRepository actionGraphSuspendedRunRepository() {
        return new InMemorySuspendedRunRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionPolicy actionGraphPermissionPolicy() {
        return new DefaultPermissionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionPolicyGuard actionGraphPolicyGuard(
            PermissionPolicy permissionPolicy,
            ActionGraphProperties properties
    ) {
        return new DefaultPolicyGuard(applyRolePolicy(permissionPolicy, properties));
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.2.0",
            value = "Spring principal resolution is experimental until STD1 identity pilots settle."
    )
    public RunPrincipalResolver actionGraphRunPrincipalResolver(ActionGraphProperties properties) {
        return new SpringSecurityRunPrincipalResolver(properties.getSecurity().getOauth2());
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.2.0",
            value = "Endpoint security mode verification is experimental until STD1 pilots settle."
    )
    public ActionGraphEndpointAccessVerifier actionGraphEndpointAccessVerifier(
            ActionGraphProperties properties,
            RunPrincipalResolver principalResolver
    ) {
        return new ActionGraphEndpointAccessVerifier(properties, principalResolver);
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphEndpointSecurityWarning")
    @Experimental(
            since = "0.2.0",
            value = "Endpoint security mode verification is experimental until STD1 pilots settle."
    )
    public SmartInitializingSingleton actionGraphEndpointSecurityWarning(ActionGraphProperties properties) {
        return () -> {
            if (properties.getSecurity().getMode() == ActionGraphProperties.EndpointSecurityMode.SHARED_SECRET) {
                LOGGER.warn("ActionGraph endpoint security mode is shared-secret; use oauth2 for production deployments");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphOpenTelemetryDependencyVerifier")
    @Experimental(
            since = "0.2.0",
            value = "OpenTelemetry observability configuration is experimental until STD2 pilots settle."
    )
    public SmartInitializingSingleton actionGraphOpenTelemetryDependencyVerifier(ActionGraphProperties properties) {
        return () -> {
            if (properties.getObservability().getOtel().isEnabled()
                    && !isClassPresent("io.opentelemetry.api.OpenTelemetry")) {
                throw new ActionGraphConfigurationException(
                        "actiongraph.observability.otel.enabled=true requires io.opentelemetry:opentelemetry-api "
                                + "on the application classpath");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ReviewAttributeContributor actionGraphReviewAttributeContributor() {
        return NoopReviewAttributeContributor.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanReviewPolicy actionGraphHumanReviewPolicy() {
        return new PendingHumanReviewPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataMaskingPolicy actionGraphDataMaskingPolicy() {
        return NoopMaskingPolicy.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationSink actionGraphObservationSink() {
        return NoopObservationSink.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "actiongraph.llm", name = "provider", havingValue = "openai-compatible")
    @Experimental(
            since = "0.1.0",
            value = "LLM auto-configuration is experimental while provider wiring conventions settle."
    )
    public LlmClient actionGraphOpenAiCompatibleLlmClient(ActionGraphProperties properties) {
        ActionGraphProperties.LlmProperties llm = properties.getLlm();
        String endpoint = requireNonBlank(llm.getBaseUrl(), "actiongraph.llm.base-url");
        String model = requireNonBlank(llm.getModel(), "actiongraph.llm.model");
        return new OpenAiCompatibleChatClient(
                endpoint,
                model,
                resolveApiKey(llm),
                llm.getHeaders(),
                llm.getTimeout()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "actiongraph.llm", name = "provider", havingValue = "deepseek")
    @Experimental(
            since = "0.1.0",
            value = "LLM auto-configuration is experimental while provider wiring conventions settle."
    )
    public LlmClient actionGraphDeepSeekLlmClient(ActionGraphProperties properties) {
        ActionGraphProperties.LlmProperties llm = properties.getLlm();
        return new DeepSeekChatClient(
                new okhttp3.OkHttpClient.Builder()
                        .callTimeout(llm.getTimeout())
                        .readTimeout(llm.getTimeout())
                        .build(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                blankToDefault(llm.getBaseUrl(), DeepSeekChatClient.DEFAULT_ENDPOINT),
                resolveApiKey(llm),
                blankToDefault(llm.getModel(), DeepSeekChatClient.DEFAULT_MODEL)
        );
    }

    @Bean
    @ConditionalOnMissingBean(Executor.class)
    public GoapExecutor actionGraphExecutor(
            Planner planner,
            ExecutionPolicyGuard policyGuard,
            HumanReviewPolicy humanReviewPolicy,
            TraceRepository traceRepository,
            SuspendedRunRepository suspendedRunRepository,
            DataMaskingPolicy maskingPolicy,
            ReviewAttributeContributor reviewAttributeContributor,
            ObservationSink observationSink,
            ActionGraphProperties properties
    ) {
        return GoapExecutor.builder()
                .planner(planner)
                .policyGuard(policyGuard)
                .humanReviewPolicy(humanReviewPolicy)
                .traceRepository(traceRepository)
                .suspendedRunRepository(suspendedRunRepository)
                .maskingPolicy(maskingPolicy)
                .reviewAttributeContributor(reviewAttributeContributor)
                .observationSink(observationSink)
                .maxSteps(properties.getExecutor().getMaxSteps())
                .durabilityEnabled(properties.getDurability().isEnabled())
                .heartbeatInterval(properties.getDurability().getHeartbeatInterval())
                .defaultEventWaitTimeout(properties.getEvents().getDefaultTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.2.0",
            value = "External event gateway auto-configuration is experimental until MS2 pilots complete."
    )
    public ExternalEventGateway actionGraphExternalEventGateway(
            GoapExecutor executor,
            SuspendedRunRepository suspendedRunRepository,
            ActionRegistry registry,
            ObjectProvider<EventApplier> eventAppliers
    ) {
        return new ExternalEventGateway(
                executor,
                suspendedRunRepository,
                registry.all(),
                registry,
                eventAppliers.orderedStream().toList()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.2.0",
            value = "External event wait sweeper auto-configuration is experimental until MS2 pilots complete."
    )
    public EventWaitSweeper actionGraphEventWaitSweeper(
            GoapExecutor executor,
            SuspendedRunRepository suspendedRunRepository,
            ActionRegistry registry
    ) {
        return new EventWaitSweeper(executor, suspendedRunRepository, registry.all(), registry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphEventWaitSweeperLifecycle")
    @Experimental(
            since = "0.2.0",
            value = "External event wait sweeping is experimental until MS2 pilots complete."
    )
    public SmartLifecycle actionGraphEventWaitSweeperLifecycle(
            EventWaitSweeper sweeper,
            ActionGraphProperties properties
    ) {
        return new EventWaitSweeperLifecycle(sweeper, properties.getEvents().getSweepPeriod());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "actiongraph.durability", name = "enabled", havingValue = "true")
    @Experimental(
            since = "0.2.0",
            value = "Crash recovery auto-configuration is experimental until MS1 recovery pilots complete."
    )
    public RunRecoverer actionGraphRunRecoverer(
            GoapExecutor executor,
            SuspendedRunRepository suspendedRunRepository,
            ActionRegistry registry,
            ActionGraphProperties properties
    ) {
        return new RunRecoverer(
                executor,
                suspendedRunRepository,
                registry.all(),
                registry,
                properties.getDurability().getRecovery()
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphRunRecovererLifecycle")
    @ConditionalOnProperty(prefix = "actiongraph.durability", name = "enabled", havingValue = "true")
    @Experimental(
            since = "0.2.0",
            value = "Crash recovery scheduling is experimental until MS1 recovery pilots complete."
    )
    public SmartLifecycle actionGraphRunRecovererLifecycle(
            RunRecoverer recoverer,
            ActionGraphProperties properties
    ) {
        return new RecovererLifecycle(recoverer, properties.getDurability().getStaleAfter(),
                properties.getDurability().getRecovererPeriod());
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionRegistry actionGraphActionRegistry(
            ObjectProvider<Action> actionBeans,
            ObjectProvider<ActionGraphContribution> contributions,
            ConfigurableListableBeanFactory beanFactory,
            ActionGraphProperties properties
    ) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        Map<String, ActionExecutionPolicy> overrides = executionPolicyOverrides(properties);
        actionBeans.orderedStream().forEach(action -> registry.register(applyExecutionPolicyOverride(action, overrides)));
        registerContributionActions(registry, contributions.orderedStream().toList(), overrides);
        if (properties.getActions().isAutoRegisterAnnotated()) {
            for (Action action : new AnnotatedSpringBeanActionRegistrar(beanFactory).annotatedActions()) {
                registry.register(applyExecutionPolicyOverride(action, overrides));
            }
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.1.0",
            value = "Contribution-driven goal catalog registration is experimental until more domains validate the packaging SPI."
    )
    public GoalCatalog actionGraphGoalCatalog(
            ObjectProvider<GoalDefinition> goalDefinitions,
            ObjectProvider<ActionGraphContribution> contributions,
            ConfigurableListableBeanFactory beanFactory,
            ActionRegistry actionRegistry,
            ActionGraphProperties properties
    ) {
        GoalCatalog catalog = new GoalCatalog();
        Map<GoalType, String> sources = new LinkedHashMap<>();
        goalDefinitions.orderedStream().forEach(goal ->
                registerGoal(catalog, sources, goal, "GoalDefinition bean"));
        for (ActionGraphContribution contribution : contributions.orderedStream().toList()) {
            for (GoalDefinition goal : contribution.goals()) {
                registerGoal(catalog, sources, goal, "contribution " + contribution.getClass().getName());
            }
            for (GoalDefinition goal : AnnotatedGoalFactory.definitions(contribution.annotatedBeans().toArray())) {
                registerGoal(catalog, sources, goal,
                        "annotated bean from contribution " + contribution.getClass().getName());
            }
        }
        if (properties.getGoals().isAutoRegisterAnnotated()) {
            for (GoalDefinition goal : new AnnotatedSpringBeanGoalRegistrar(beanFactory).annotatedGoals()) {
                registerGoal(catalog, sources, goal, "annotated Spring bean");
            }
        }
        GoalValueConverterResolver converterResolver =
                new AnnotatedSpringBeanGoalSeederRegistrar(beanFactory, catalog).converterResolver();
        ConfiguredGoalDefinitionFactory factory = new ConfiguredGoalDefinitionFactory(converterResolver);
        for (ConfiguredGoalDefinition configured : configuredGoalDefinitions(properties)) {
            if (configured.enabled()) {
                registerGoal(catalog, sources, factory.definition(configured), configured.source());
            }
        }
        List<SourcedGoalDefinition> bundledGoals = loadBundleGoalDefinitions(properties, factory,
                ActionGraphFingerprints.actionGraph(actionRegistry.all()));
        bundledGoals.forEach(goal -> registerGoal(catalog, sources, goal.definition(), goal.source()));
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.1.0",
            value = "Contribution-driven seeder registration is experimental until more domains validate the packaging SPI."
    )
    public GoalBlackboardSeederRegistry actionGraphGoalBlackboardSeederRegistry(
            ObjectProvider<GoalBlackboardSeeder> seeders,
            ObjectProvider<ActionGraphContribution> contributions,
            GoalCatalog catalog,
            ConfigurableListableBeanFactory beanFactory,
            ActionGraphProperties properties
    ) {
        GoalBlackboardSeederRegistry registry = new GoalBlackboardSeederRegistry();
        AnnotatedSpringBeanGoalSeederRegistrar annotatedSeederRegistrar =
                new AnnotatedSpringBeanGoalSeederRegistrar(beanFactory, catalog);
        GoalValueConverterResolver converterResolver = annotatedSeederRegistrar.converterResolver();
        seeders.orderedStream().forEach(seeder -> registerSeeder(registry, catalog, seeder));
        Map<GoalType, String> sources = new LinkedHashMap<>();
        for (ActionGraphContribution contribution : contributions.orderedStream().toList()) {
            List<GoalBlackboardSeeder> contributionSeeders = new java.util.ArrayList<>(contribution.seeders());
            contributionSeeders.addAll(AnnotatedGoalSeederFactory.seeders(
                    converterResolver,
                    catalog.all(),
                    contribution.annotatedBeans().toArray()
            ));
            for (GoalBlackboardSeeder seeder : contributionSeeders) {
                String previous = sources.putIfAbsent(seeder.goalType(), contribution.getClass().getName());
                if (previous != null) {
                    throw new ActionGraphConfigurationException("Duplicate blackboard seeder for goal type "
                            + seeder.goalType().value() + " from contributions " + previous
                            + " and " + contribution.getClass().getName());
                }
                registerSeeder(registry, catalog, seeder);
            }
        }
        if (properties.getSeeders().isAutoRegisterAnnotated()) {
            for (GoalBlackboardSeeder seeder : annotatedSeederRegistrar.annotatedSeeders()) {
                registerSeeder(registry, catalog, seeder);
            }
        }
        registry.registerDefaultSeeders(catalog, converterResolver, properties.getSeeding().isAuto());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    @Experimental(
            since = "0.2.0",
            value = "The root ActionGraph Spring bean is experimental until DX2 pilots validate the golden path."
    )
    public ActionGraph actionGraph(
            GoalCatalog catalog,
            GoalBlackboardSeederRegistry seeders,
            GoapExecutor executor,
            ActionRegistry registry,
            ObjectProvider<GoalInterpreter> interpreter,
            ActionGraphProperties properties
    ) {
        ActionGraph.Builder builder = ActionGraph.builder()
                .goalCatalog(catalog)
                .seeders(seeders)
                .executor(executor)
                .actionRegistry(registry)
                .autoSeeding(properties.getSeeding().isAuto());
        GoalInterpreter goalInterpreter = interpreter.getIfAvailable();
        if (goalInterpreter != null) {
            builder.goalInterpreter(goalInterpreter);
        }
        ActionGraph actionGraph = builder.build();
        LOGGER.info("ActionGraph actionGraphFingerprint={}", actionGraph.actionGraphFingerprint());
        return actionGraph;
    }

    @Bean
    @ConditionalOnBean({GoalCatalog.class, ActionRegistry.class})
    @Experimental(
            since = "0.1.0",
            value = "Startup graph validation is experimental while diagnostics are validated in pilots."
    )
    public SmartInitializingSingleton actionGraphValidationRunner(
            GoalCatalog catalog,
            ActionRegistry registry,
            ActionGraphProperties properties
    ) {
        return () -> {
            if (properties.getValidation().getMode() == ActionGraphProperties.ValidationMode.OFF) {
                return;
            }
            var report = new ActionGraphValidator().validate(catalog, registry.all());
            if (report.valid()) {
                return;
            }
            String message = report.formatText();
            if (properties.getValidation().getMode() == ActionGraphProperties.ValidationMode.WARN) {
                LOGGER.warn("ActionGraph validation failed: {}", message);
                return;
            }
            throw new ActionGraphConfigurationException("ActionGraph validation failed: " + message);
        };
    }

    private void registerContributionActions(
            DefaultActionRegistry registry,
            List<ActionGraphContribution> contributions,
            Map<String, ActionExecutionPolicy> overrides
    ) {
        Map<ActionId, String> sources = new LinkedHashMap<>();
        for (ActionGraphContribution contribution : contributions) {
            List<Action> contributionActions = new java.util.ArrayList<>(contribution.actions());
            contributionActions.addAll(AnnotatedActionFactory.actions(contribution.annotatedBeans().toArray()));
            for (Action action : contributionActions) {
                String source = contribution.getClass().getName();
                String previous = sources.putIfAbsent(action.id(), source);
                if (previous != null) {
                    throw new ActionGraphConfigurationException("Duplicate action id " + action.id().value()
                            + " from contributions " + previous + " and " + source);
                }
                registry.register(applyExecutionPolicyOverride(action, overrides));
            }
        }
    }

    private void registerGoal(
            GoalCatalog catalog,
            Map<GoalType, String> sources,
            GoalDefinition goal,
            String source
    ) {
        String previous = sources.putIfAbsent(goal.type(), source);
        if (previous != null) {
            throw new ActionGraphConfigurationException("Duplicate goal type " + goal.type().value()
                    + " from " + previous + " and " + source);
        }
        catalog.register(goal);
    }

    private List<ConfiguredGoalDefinition> configuredGoalDefinitions(ActionGraphProperties properties) {
        List<ConfiguredGoalDefinition> definitions = new java.util.ArrayList<>();
        List<ActionGraphProperties.GoalDefinitionProperties> configured =
                properties.getGoals().getDefinitions();
        for (int i = 0; i < configured.size(); i++) {
            ActionGraphProperties.GoalDefinitionProperties definition = configured.get(i);
            definitions.add(configuredGoalDefinition(definition, "actiongraph.goals.definitions[" + i + "]"));
        }
        return List.copyOf(definitions);
    }

    private ConfiguredGoalDefinition configuredGoalDefinition(
            ActionGraphProperties.GoalDefinitionProperties definition,
            String source
    ) {
        List<ConfiguredGoalParameter> parameters = new java.util.ArrayList<>();
        for (ActionGraphProperties.GoalParameterProperties parameter : definition.getParameters()) {
            parameters.add(new ConfiguredGoalParameter(
                    parameter.getName(),
                    parameter.getType(),
                    parameter.isRequired(),
                    parameter.getDescription(),
                    parameter.getExample()
            ));
        }
        return new ConfiguredGoalDefinition(
                definition.getType(),
                definition.getDescription(),
                definition.isEnabled(),
                definition.getTargetConditions(),
                definition.getSeedConditions(),
                parameters,
                source
        );
    }

    private List<SourcedGoalDefinition> loadBundleGoalDefinitions(
            ActionGraphProperties properties,
            ConfiguredGoalDefinitionFactory factory,
            String currentActionGraphFingerprint
    ) {
        List<SourcedGoalDefinition> definitions = new java.util.ArrayList<>();
        List<String> locations = properties.getGoals().getBundle().getLocations();
        for (String location : locations) {
            if (location == null || location.isBlank()) {
                throw new ActionGraphConfigurationException("actiongraph.goals.bundle.locations must not contain blanks");
            }
            BundleReadResult bundle = readBundle(location.trim(), factory);
            if (!bundle.bundleFingerprint().equals(bundle.declaredBundleFingerprint())) {
                throw new ActionGraphConfigurationException("Goal bundle " + location
                        + " fingerprint self-check failed: declared " + bundle.declaredBundleFingerprint()
                        + " but computed " + bundle.bundleFingerprint());
            }
            if (!bundle.actionGraphFingerprint().equals(currentActionGraphFingerprint)) {
                String message = "Goal bundle " + location + " was validated against action graph fingerprint "
                        + bundle.actionGraphFingerprint() + " but current fingerprint is "
                        + currentActionGraphFingerprint;
                if (properties.getGoals().getBundle().getFingerprintMismatch()
                        == ActionGraphProperties.FingerprintMismatchMode.FAIL) {
                    throw new ActionGraphConfigurationException(message);
                }
                LOGGER.warn(message);
            }
            definitions.addAll(bundle.definitions());
        }
        return List.copyOf(definitions);
    }

    private BundleReadResult readBundle(String location, ConfiguredGoalDefinitionFactory factory) {
        Resource resource = new DefaultResourceLoader().getResource(location);
        if (!resource.exists()) {
            throw new ActionGraphConfigurationException("Goal bundle does not exist: " + location);
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties properties = yaml.getObject();
        if (properties == null) {
            throw new ActionGraphConfigurationException("Goal bundle is empty: " + location);
        }
        String declaredBundleFingerprint = requireProperty(properties,
                "actiongraph-bundle.bundle-fingerprint", location);
        String actionGraphFingerprint = requireProperty(properties,
                "actiongraph-bundle.action-graph-fingerprint", location);
        List<ConfiguredGoalDefinition> configured = parseConfiguredGoalDefinitions(
                properties, "actiongraph-bundle.definitions", location);
        List<SourcedGoalDefinition> definitions = configured.stream()
                .filter(ConfiguredGoalDefinition::enabled)
                .map(declaration -> new SourcedGoalDefinition(factory.definition(declaration), declaration.source()))
                .toList();
        List<GoalDefinition> rawDefinitions = definitions.stream()
                .map(SourcedGoalDefinition::definition)
                .toList();
        return new BundleReadResult(
                List.copyOf(definitions),
                declaredBundleFingerprint,
                actionGraphFingerprint,
                ActionGraphFingerprints.goals(rawDefinitions)
        );
    }

    private List<ConfiguredGoalDefinition> parseConfiguredGoalDefinitions(
            Properties properties,
            String prefix,
            String source
    ) {
        List<ConfiguredGoalDefinition> definitions = new java.util.ArrayList<>();
        for (int index : indexes(properties, prefix)) {
            String base = prefix + "[" + index + "]";
            definitions.add(new ConfiguredGoalDefinition(
                    property(properties, base + ".type"),
                    property(properties, base + ".description"),
                    Boolean.parseBoolean(property(properties, base + ".enabled", "true")),
                    list(properties, base + ".target-conditions"),
                    list(properties, base + ".seed-conditions"),
                    parseConfiguredGoalParameters(properties, base + ".parameters"),
                    source + "#" + base
            ));
        }
        return List.copyOf(definitions);
    }

    private List<ConfiguredGoalParameter> parseConfiguredGoalParameters(Properties properties, String prefix) {
        List<ConfiguredGoalParameter> parameters = new java.util.ArrayList<>();
        for (int index : indexes(properties, prefix)) {
            String base = prefix + "[" + index + "]";
            parameters.add(new ConfiguredGoalParameter(
                    property(properties, base + ".name"),
                    property(properties, base + ".type", "string"),
                    Boolean.parseBoolean(property(properties, base + ".required", "true")),
                    property(properties, base + ".description"),
                    property(properties, base + ".example")
            ));
        }
        return List.copyOf(parameters);
    }

    private List<Integer> indexes(Properties properties, String prefix) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "\\[(\\d+)]\\..+");
        return properties.stringPropertyNames().stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> list(Properties properties, String prefix) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "\\[(\\d+)]");
        return properties.stringPropertyNames().stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .sorted(java.util.Comparator.comparingInt(matcher -> Integer.parseInt(matcher.group(1))))
                .map(matcher -> properties.getProperty(matcher.group()))
                .toList();
    }

    private String requireProperty(Properties properties, String key, String source) {
        String value = property(properties, key);
        if (value.isBlank()) {
            throw new ActionGraphConfigurationException("Goal bundle " + source
                    + " is missing required property " + key);
        }
        return value;
    }

    private String property(Properties properties, String key) {
        return property(properties, key, "");
    }

    private String property(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : value;
    }

    private void registerSeeder(
            GoalBlackboardSeederRegistry registry,
            GoalCatalog catalog,
            GoalBlackboardSeeder seeder
    ) {
        assertDeclaredSeedConditions(catalog, seeder);
        registry.register(seeder);
    }

    private void assertDeclaredSeedConditions(GoalCatalog catalog, GoalBlackboardSeeder seeder) {
        seeder.declaredSeedConditions().ifPresent(declared -> catalog.byType(seeder.goalType()).ifPresent(goal -> {
            Set<com.actiongraph.planning.Condition> missing = new LinkedHashSet<>(goal.seedConditions());
            missing.removeAll(declared);
            if (!missing.isEmpty()) {
                throw new ActionGraphConfigurationException(
                        "Annotated blackboard seeder for goal type " + seeder.goalType().value()
                                + " does not declare required seed conditions " + missing
                                + "; goal declares " + goal.seedConditions()
                                + " but seeder declares " + declared);
            }
        }));
    }

    private record BundleReadResult(
            List<SourcedGoalDefinition> definitions,
            String declaredBundleFingerprint,
            String actionGraphFingerprint,
            String bundleFingerprint
    ) {
    }

    private record SourcedGoalDefinition(GoalDefinition definition, String source) {
    }

    private Map<String, ActionExecutionPolicy> executionPolicyOverrides(ActionGraphProperties properties) {
        Map<String, ActionExecutionPolicy> overrides = new LinkedHashMap<>();
        for (ActionGraphProperties.ExecutionPolicyProperties policy : properties.getExecution().getPolicies()) {
            if (policy.getActionId() == null || policy.getActionId().isBlank()) {
                throw new IllegalArgumentException("actiongraph.execution.policies action-id must not be blank");
            }
            overrides.put(policy.getActionId(), new ActionExecutionPolicy(
                    policy.getMaxAttempts() == null ? 1 : policy.getMaxAttempts(),
                    policy.getBackoff() == null ? Duration.ZERO : policy.getBackoff(),
                    policy.getTimeout()
            ));
        }
        return Map.copyOf(overrides);
    }

    private Action applyExecutionPolicyOverride(Action action, Map<String, ActionExecutionPolicy> overrides) {
        ActionExecutionPolicy override = overrides.get(action.id().value());
        return override == null ? action : new ExecutionPolicyOverrideAction(action, override);
    }

    private PermissionPolicy applyRolePolicy(PermissionPolicy permissionPolicy, ActionGraphProperties properties) {
        if (properties.getSecurity().getActionRoles().isEmpty()) {
            return permissionPolicy;
        }
        Map<String, Set<String>> roleMap = new LinkedHashMap<>();
        for (ActionGraphProperties.ActionRoleProperties role : properties.getSecurity().getActionRoles()) {
            if (role.getActionId() == null || role.getActionId().isBlank()) {
                throw new IllegalArgumentException("actiongraph.security.action-roles action-id must not be blank");
            }
            if (role.getAnyOf().isEmpty()) {
                throw new IllegalArgumentException("actiongraph.security.action-roles any-of must not be empty for "
                        + role.getActionId());
            }
            roleMap.put(role.getActionId(), Set.copyOf(role.getAnyOf()));
        }
        return new RolePermissionPolicy(permissionPolicy, roleMap);
    }

    private String resolveApiKey(ActionGraphProperties.LlmProperties llm) {
        String envName = requireNonBlank(llm.getApiKeyEnv(), "actiongraph.llm.api-key-env");
        String apiKey = System.getenv(envName);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ActionGraphConfigurationException(
                    "Environment variable " + envName + " configured by actiongraph.llm.api-key-env is not set"
            );
        }
        return apiKey;
    }

    private String requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new ActionGraphConfigurationException(propertyName + " must not be blank");
        }
        return value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    private static final class RecovererLifecycle implements SmartLifecycle {
        private final RunRecoverer recoverer;
        private final Duration staleAfter;
        private final Duration period;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread thread;

        private RecovererLifecycle(RunRecoverer recoverer, Duration staleAfter, Duration period) {
            this.recoverer = recoverer;
            this.staleAfter = staleAfter;
            this.period = period;
        }

        @Override
        public void start() {
            if (period.isZero() || !running.compareAndSet(false, true)) {
                return;
            }
            thread = Thread.ofVirtual().name("actiongraph-run-recoverer").start(() -> {
                while (running.get()) {
                    try {
                        recoverer.recoverOne(Instant.now().minus(staleAfter));
                        Thread.sleep(period.toMillis());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (RuntimeException ex) {
                        LOGGER.debug("ActionGraph recoverer iteration failed", ex);
                    }
                }
            });
        }

        @Override
        public void stop() {
            running.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }
    }

    private static final class EventWaitSweeperLifecycle implements SmartLifecycle {
        private final EventWaitSweeper sweeper;
        private final Duration period;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread thread;

        private EventWaitSweeperLifecycle(EventWaitSweeper sweeper, Duration period) {
            this.sweeper = sweeper;
            this.period = period;
        }

        @Override
        public void start() {
            if (period.isZero() || !running.compareAndSet(false, true)) {
                return;
            }
            thread = Thread.ofVirtual().name("actiongraph-event-wait-sweeper").start(() -> {
                while (running.get()) {
                    try {
                        sweeper.sweepOne(Instant.now());
                        Thread.sleep(period.toMillis());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (RuntimeException ex) {
                        LOGGER.debug("ActionGraph event wait sweeper iteration failed", ex);
                    }
                }
            });
        }

        @Override
        public void stop() {
            running.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }
    }
}
