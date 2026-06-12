package com.actiongraph.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;
import com.actiongraph.durability.RecoveryPolicy;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.runtime.GoapExecutor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "actiongraph")
public class ActionGraphProperties {
    private final PlannerProperties planner = new PlannerProperties();
    private final ExecutorProperties executor = new ExecutorProperties();
    private final ActionsProperties actions = new ActionsProperties();
    private final GoalsProperties goals = new GoalsProperties();
    private final SeedingProperties seeding = new SeedingProperties();
    private final SeedersProperties seeders = new SeedersProperties();
    private final InterpretationProperties interpretation = new InterpretationProperties();
    private final ValidationProperties validation = new ValidationProperties();
    private final ExecutionProperties execution = new ExecutionProperties();
    private final ObservabilityProperties observability = new ObservabilityProperties();
    private final LlmProperties llm = new LlmProperties();
    private final DurabilityProperties durability = new DurabilityProperties();
    private final EventsProperties events = new EventsProperties();
    private final SecurityProperties security = new SecurityProperties();

    public PlannerProperties getPlanner() {
        return planner;
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    public ActionsProperties getActions() {
        return actions;
    }

    @Experimental(
            since = "0.2.0",
            value = "Annotated goal auto-registration is experimental until DX pilots validate schema conventions."
    )
    public GoalsProperties getGoals() {
        return goals;
    }

    @Experimental(
            since = "0.2.0",
            value = "Automatic schema seeding configuration is experimental until DX3 pilots settle."
    )
    public SeedingProperties getSeeding() {
        return seeding;
    }

    @Experimental(
            since = "0.2.0",
            value = "Annotated seeder auto-registration is experimental until DX pilots validate binding conventions."
    )
    public SeedersProperties getSeeders() {
        return seeders;
    }

    @Experimental(
            since = "0.2.0",
            value = "Interpretation quality measurement is experimental until STD3 pilots settle."
    )
    public InterpretationProperties getInterpretation() {
        return interpretation;
    }

    @Experimental(
            since = "0.1.0",
            value = "Startup graph validation configuration is experimental while diagnostics are validated in pilots."
    )
    public ValidationProperties getValidation() {
        return validation;
    }

    @Experimental(
            since = "0.1.0",
            value = "Configuration-based execution policy overrides are experimental until retry conventions settle."
    )
    public ExecutionProperties getExecution() {
        return execution;
    }

    @Experimental(
            since = "0.2.0",
            value = "OpenTelemetry observability configuration is experimental until STD2 pilots settle."
    )
    public ObservabilityProperties getObservability() {
        return observability;
    }

    @Experimental(
            since = "0.1.0",
            value = "LLM auto-configuration properties are experimental while provider wiring conventions settle."
    )
    public LlmProperties getLlm() {
        return llm;
    }

    @Experimental(
            since = "0.2.0",
            value = "Durability configuration is experimental until MS1 crash-recovery pilots complete."
    )
    public DurabilityProperties getDurability() {
        return durability;
    }

    @Experimental(
            since = "0.2.0",
            value = "External event ingress configuration is experimental until MS2 pilots complete."
    )
    public EventsProperties getEvents() {
        return events;
    }

    @Experimental(
            since = "0.2.0",
            value = "Principal-aware endpoint and role security is experimental until STD1 pilots settle."
    )
    public SecurityProperties getSecurity() {
        return security;
    }

    public static final class PlannerProperties {
        private int maxDepth = GoapPlanner.DEFAULT_MAX_DEPTH;
        private int maxExpansions = GoapPlanner.DEFAULT_MAX_EXPANSIONS;

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getMaxExpansions() {
            return maxExpansions;
        }

        public void setMaxExpansions(int maxExpansions) {
            this.maxExpansions = maxExpansions;
        }
    }

    public static final class ExecutorProperties {
        private int maxSteps = GoapExecutor.DEFAULT_MAX_STEPS;

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }
    }

    public static final class ActionsProperties {
        private boolean autoRegisterAnnotated = true;

        public boolean isAutoRegisterAnnotated() {
            return autoRegisterAnnotated;
        }

        public void setAutoRegisterAnnotated(boolean autoRegisterAnnotated) {
            this.autoRegisterAnnotated = autoRegisterAnnotated;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Annotated goal auto-registration is experimental until DX pilots validate schema conventions."
    )
    public static final class GoalsProperties {
        private boolean autoRegisterAnnotated = true;
        private final List<GoalDefinitionProperties> definitions = new ArrayList<>();
        private final GoalBundleProperties bundle = new GoalBundleProperties();

        public boolean isAutoRegisterAnnotated() {
            return autoRegisterAnnotated;
        }

        public void setAutoRegisterAnnotated(boolean autoRegisterAnnotated) {
            this.autoRegisterAnnotated = autoRegisterAnnotated;
        }

        @Experimental(
                since = "0.2.0",
                value = "External goal configuration is experimental until DX4 pilots validate bundle conventions."
        )
        public List<GoalDefinitionProperties> getDefinitions() {
            return definitions;
        }

        @Experimental(
                since = "0.2.0",
                value = "Goal bundle import is experimental until DX4 pilots validate promotion workflows."
        )
        public GoalBundleProperties getBundle() {
            return bundle;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "External goal configuration is experimental until DX4 pilots validate bundle conventions."
    )
    public static final class GoalDefinitionProperties {
        private String type;
        private String description = "";
        private boolean enabled = true;
        private List<String> targetConditions = new ArrayList<>();
        private List<String> seedConditions = new ArrayList<>();
        private final List<GoalParameterProperties> parameters = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description == null ? "" : description;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getTargetConditions() {
            return targetConditions;
        }

        public void setTargetConditions(List<String> targetConditions) {
            this.targetConditions = targetConditions == null ? new ArrayList<>() : new ArrayList<>(targetConditions);
        }

        public List<String> getSeedConditions() {
            return seedConditions;
        }

        public void setSeedConditions(List<String> seedConditions) {
            this.seedConditions = seedConditions == null ? new ArrayList<>() : new ArrayList<>(seedConditions);
        }

        public List<GoalParameterProperties> getParameters() {
            return parameters;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "External goal parameter configuration is experimental until DX4 pilots validate bundle conventions."
    )
    public static final class GoalParameterProperties {
        private String name;
        private String type = "string";
        private boolean required = true;
        private String description = "";
        private String example = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type == null || type.isBlank() ? "string" : type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description == null ? "" : description;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example == null ? "" : example;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Goal bundle import is experimental until DX4 pilots validate promotion workflows."
    )
    public static final class GoalBundleProperties {
        private List<String> locations = new ArrayList<>();
        private FingerprintMismatchMode fingerprintMismatch = FingerprintMismatchMode.FAIL;

        public List<String> getLocations() {
            return locations;
        }

        public void setLocations(List<String> locations) {
            this.locations = locations == null ? new ArrayList<>() : new ArrayList<>(locations);
        }

        public FingerprintMismatchMode getFingerprintMismatch() {
            return fingerprintMismatch;
        }

        public void setFingerprintMismatch(FingerprintMismatchMode fingerprintMismatch) {
            this.fingerprintMismatch = fingerprintMismatch == null
                    ? FingerprintMismatchMode.FAIL
                    : fingerprintMismatch;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Goal bundle fingerprint policy is experimental until DX4 promotion workflows settle."
    )
    public enum FingerprintMismatchMode {
        FAIL,
        WARN
    }

    @Experimental(
            since = "0.2.0",
            value = "Automatic schema seeding configuration is experimental until DX3 pilots settle."
    )
    public static final class SeedingProperties {
        private boolean auto = true;

        public boolean isAuto() {
            return auto;
        }

        public void setAuto(boolean auto) {
            this.auto = auto;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Annotated seeder auto-registration is experimental until DX pilots validate binding conventions."
    )
    public static final class SeedersProperties {
        private boolean autoRegisterAnnotated = true;

        public boolean isAutoRegisterAnnotated() {
            return autoRegisterAnnotated;
        }

        public void setAutoRegisterAnnotated(boolean autoRegisterAnnotated) {
            this.autoRegisterAnnotated = autoRegisterAnnotated;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Interpretation quality measurement is experimental until STD3 pilots settle."
    )
    public static final class InterpretationProperties {
        private boolean metrics = false;
        private final InterpretationSamplingProperties sampling = new InterpretationSamplingProperties();

        public boolean isMetrics() {
            return metrics;
        }

        public void setMetrics(boolean metrics) {
            this.metrics = metrics;
        }

        public InterpretationSamplingProperties getSampling() {
            return sampling;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Interpretation quality sampling is experimental until STD3 pilots settle."
    )
    public static final class InterpretationSamplingProperties {
        private double rate = 0.0d;

        public double getRate() {
            return rate;
        }

        public void setRate(double rate) {
            if (Double.isNaN(rate) || rate < 0.0d || rate > 1.0d) {
                throw new IllegalArgumentException("rate must be between 0 and 1");
            }
            this.rate = rate;
        }
    }

    @Experimental(
            since = "0.1.0",
            value = "Startup graph validation configuration is experimental while diagnostics are validated in pilots."
    )
    public static final class ValidationProperties {
        private ValidationMode mode = ValidationMode.FAIL;

        public ValidationMode getMode() {
            return mode;
        }

        public void setMode(ValidationMode mode) {
            this.mode = mode;
        }
    }

    @Experimental(
            since = "0.1.0",
            value = "Startup graph validation configuration is experimental while diagnostics are validated in pilots."
    )
    public enum ValidationMode {
        FAIL,
        WARN,
        OFF
    }

    @Experimental(
            since = "0.1.0",
            value = "Configuration-based execution policy overrides are experimental until retry conventions settle."
    )
    public static final class ExecutionProperties {
        private final List<ExecutionPolicyProperties> policies = new ArrayList<>();

        public List<ExecutionPolicyProperties> getPolicies() {
            return policies;
        }
    }

    @Experimental(
            since = "0.1.0",
            value = "Configuration-based execution policy overrides are experimental until retry conventions settle."
    )
    public static final class ExecutionPolicyProperties {
        private String actionId;
        private Integer maxAttempts;
        private Duration backoff;
        private Duration timeout;

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "OpenTelemetry observability configuration is experimental until STD2 pilots settle."
    )
    public static final class ObservabilityProperties {
        private final OpenTelemetryProperties otel = new OpenTelemetryProperties();

        public OpenTelemetryProperties getOtel() {
            return otel;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "OpenTelemetry GenAI semantic convention export is experimental until STD2 pilots settle."
    )
    public static final class OpenTelemetryProperties {
        private boolean enabled = false;
        private String instrumentationName = "actiongraph";
        private boolean includeRunId = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInstrumentationName() {
            return instrumentationName;
        }

        public void setInstrumentationName(String instrumentationName) {
            this.instrumentationName = instrumentationName == null || instrumentationName.isBlank()
                    ? "actiongraph"
                    : instrumentationName.trim();
        }

        public boolean isIncludeRunId() {
            return includeRunId;
        }

        public void setIncludeRunId(boolean includeRunId) {
            this.includeRunId = includeRunId;
        }
    }

    @Experimental(
            since = "0.1.0",
            value = "LLM auto-configuration properties are experimental while provider wiring conventions settle."
    )
    public static final class LlmProperties {
        private LlmProvider provider = LlmProvider.NONE;
        private String baseUrl;
        private String model;
        private String apiKeyEnv;
        private Duration timeout = Duration.ofSeconds(20);
        private final Map<String, String> headers = new LinkedHashMap<>();

        public LlmProvider getProvider() {
            return provider;
        }

        public void setProvider(LlmProvider provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers.clear();
            if (headers != null) {
                this.headers.putAll(headers);
            }
        }

        public void setApiKey(String ignored) {
            throw new ActionGraphConfigurationException(
                    "actiongraph.llm.api-key is not supported. Use actiongraph.llm.api-key-env instead."
            );
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Durability configuration is experimental until MS1 crash-recovery pilots complete."
    )
    public static final class DurabilityProperties {
        private boolean enabled = false;
        private RecoveryPolicy recovery = RecoveryPolicy.CONTINUE;
        private Duration heartbeatInterval = GoapExecutor.DEFAULT_HEARTBEAT_INTERVAL;
        private Duration staleAfter = Duration.ofMinutes(5);
        private Duration recovererPeriod = Duration.ofSeconds(60);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RecoveryPolicy getRecovery() {
            return recovery;
        }

        public void setRecovery(RecoveryPolicy recovery) {
            this.recovery = recovery;
        }

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = requirePositiveDuration(heartbeatInterval, "heartbeatInterval");
        }

        public Duration getStaleAfter() {
            return staleAfter;
        }

        public void setStaleAfter(Duration staleAfter) {
            this.staleAfter = requirePositiveDuration(staleAfter, "staleAfter");
        }

        public Duration getRecovererPeriod() {
            return recovererPeriod;
        }

        public void setRecovererPeriod(Duration recovererPeriod) {
            Duration value = java.util.Objects.requireNonNull(recovererPeriod, "recovererPeriod");
            if (value.isNegative()) {
                throw new IllegalArgumentException("recovererPeriod must not be negative");
            }
            this.recovererPeriod = value;
        }

        private static Duration requirePositiveDuration(Duration duration, String name) {
            Duration value = java.util.Objects.requireNonNull(duration, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "External event ingress configuration is experimental until MS2 pilots complete."
    )
    public static final class EventsProperties {
        private Duration defaultTimeout = GoapExecutor.DEFAULT_EVENT_WAIT_TIMEOUT;
        private Duration sweepPeriod = Duration.ofSeconds(60);
        private final CallbackEndpointProperties callbackEndpoint = new CallbackEndpointProperties();

        public Duration getDefaultTimeout() {
            return defaultTimeout;
        }

        public void setDefaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = DurabilityProperties.requirePositiveDuration(defaultTimeout, "defaultTimeout");
        }

        public Duration getSweepPeriod() {
            return sweepPeriod;
        }

        public void setSweepPeriod(Duration sweepPeriod) {
            Duration value = java.util.Objects.requireNonNull(sweepPeriod, "sweepPeriod");
            if (value.isNegative()) {
                throw new IllegalArgumentException("sweepPeriod must not be negative");
            }
            this.sweepPeriod = value;
        }

        public CallbackEndpointProperties getCallbackEndpoint() {
            return callbackEndpoint;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Principal-aware endpoint and role security is experimental until STD1 pilots settle."
    )
    public static final class SecurityProperties {
        private EndpointSecurityMode mode = EndpointSecurityMode.SHARED_SECRET;
        private final OAuth2Properties oauth2 = new OAuth2Properties();
        private final EndpointScopesProperties endpoints = new EndpointScopesProperties();
        private final List<ActionRoleProperties> actionRoles = new ArrayList<>();

        public EndpointSecurityMode getMode() {
            return mode;
        }

        public void setMode(EndpointSecurityMode mode) {
            this.mode = mode == null ? EndpointSecurityMode.SHARED_SECRET : mode;
        }

        public OAuth2Properties getOauth2() {
            return oauth2;
        }

        public EndpointScopesProperties getEndpoints() {
            return endpoints;
        }

        public List<ActionRoleProperties> getActionRoles() {
            return actionRoles;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Endpoint security mode selection is experimental until STD1 pilots settle."
    )
    public enum EndpointSecurityMode {
        SHARED_SECRET,
        OAUTH2
    }

    @Experimental(
            since = "0.2.0",
            value = "OAuth2 claim mapping is experimental until STD1 pilots settle."
    )
    public static final class OAuth2Properties {
        private String rolesClaim = "roles";
        private String clientIdClaim = "azp";
        private String fallbackClientIdClaim = "client_id";
        private String delegationChainClaim = "";

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = blankToDefault(rolesClaim, "roles");
        }

        public String getClientIdClaim() {
            return clientIdClaim;
        }

        public void setClientIdClaim(String clientIdClaim) {
            this.clientIdClaim = blankToDefault(clientIdClaim, "azp");
        }

        public String getFallbackClientIdClaim() {
            return fallbackClientIdClaim;
        }

        public void setFallbackClientIdClaim(String fallbackClientIdClaim) {
            this.fallbackClientIdClaim = blankToDefault(fallbackClientIdClaim, "client_id");
        }

        public String getDelegationChainClaim() {
            return delegationChainClaim;
        }

        public void setDelegationChainClaim(String delegationChainClaim) {
            this.delegationChainClaim = delegationChainClaim == null ? "" : delegationChainClaim.trim();
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Endpoint scope mapping is experimental until STD1 pilots settle."
    )
    public static final class EndpointScopesProperties {
        private List<String> console = new ArrayList<>(List.of("actiongraph.console"));
        private List<String> studio = new ArrayList<>(List.of("actiongraph.studio"));
        private List<String> runtimeApi = new ArrayList<>(List.of("actiongraph.runtime"));
        private List<String> humanReview = new ArrayList<>(List.of("actiongraph.human-review"));
        private List<String> events = new ArrayList<>(List.of("actiongraph.events"));

        public List<String> getConsole() {
            return List.copyOf(console);
        }

        public void setConsole(List<String> console) {
            this.console = safeScopeList(console, "console");
        }

        public List<String> getStudio() {
            return List.copyOf(studio);
        }

        public void setStudio(List<String> studio) {
            this.studio = safeScopeList(studio, "studio");
        }

        public List<String> getRuntimeApi() {
            return List.copyOf(runtimeApi);
        }

        public void setRuntimeApi(List<String> runtimeApi) {
            this.runtimeApi = safeScopeList(runtimeApi, "runtime-api");
        }

        public List<String> getHumanReview() {
            return List.copyOf(humanReview);
        }

        public void setHumanReview(List<String> humanReview) {
            this.humanReview = safeScopeList(humanReview, "human-review");
        }

        public List<String> getEvents() {
            return List.copyOf(events);
        }

        public void setEvents(List<String> events) {
            this.events = safeScopeList(events, "events");
        }

        private static List<String> safeScopeList(List<String> scopes, String name) {
            if (scopes == null) {
                return new ArrayList<>();
            }
            List<String> safeScopes = new ArrayList<>();
            for (String scope : scopes) {
                if (scope == null || scope.isBlank()) {
                    throw new IllegalArgumentException("actiongraph.security.endpoints." + name
                            + " scopes must not contain blanks");
                }
                safeScopes.add(scope.trim());
            }
            return safeScopes;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "Configuration-based action role gates are experimental until STD1 pilots settle."
    )
    public static final class ActionRoleProperties {
        private String actionId;
        private List<String> anyOf = new ArrayList<>();

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public List<String> getAnyOf() {
            return List.copyOf(anyOf);
        }

        public void setAnyOf(List<String> anyOf) {
            if (anyOf == null) {
                this.anyOf = new ArrayList<>();
                return;
            }
            List<String> safeRoles = new ArrayList<>();
            for (String role : anyOf) {
                if (role == null || role.isBlank()) {
                    throw new IllegalArgumentException("actiongraph.security.action-roles any-of must not contain blanks");
                }
                safeRoles.add(role.trim());
            }
            this.anyOf = safeRoles;
        }
    }

    @Experimental(
            since = "0.2.0",
            value = "External event HTTP callbacks are experimental until MS2 pilots complete."
    )
    public static final class CallbackEndpointProperties implements SharedSecretTokenProperties {
        private boolean enabled = false;
        private String path = "/actiongraph/events";
        private String tokenHeader = "X-ActionGraph-Event-Token";
        private String sharedSecret = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("event callback endpoint path must not be blank");
            }
            this.path = path;
        }

        @Override
        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            if (tokenHeader == null || tokenHeader.isBlank()) {
                throw new IllegalArgumentException("event callback endpoint token header must not be blank");
            }
            this.tokenHeader = tokenHeader;
        }

        @Override
        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            if (sharedSecret != null && sharedSecret.isBlank()) {
                throw new IllegalArgumentException("event callback endpoint shared secret must not be blank");
            }
            this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
        }
    }

    @Experimental(
            since = "0.1.0",
            value = "LLM auto-configuration properties are experimental while provider wiring conventions settle."
    )
    public enum LlmProvider {
        NONE,
        DEEPSEEK,
        OPENAI_COMPATIBLE
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
