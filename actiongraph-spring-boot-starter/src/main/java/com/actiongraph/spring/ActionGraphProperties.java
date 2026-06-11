package com.actiongraph.spring;

import com.actiongraph.api.Experimental;
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
    private final ValidationProperties validation = new ValidationProperties();
    private final ExecutionProperties execution = new ExecutionProperties();
    private final LlmProperties llm = new LlmProperties();

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
            since = "0.1.0",
            value = "LLM auto-configuration properties are experimental while provider wiring conventions settle."
    )
    public LlmProperties getLlm() {
        return llm;
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
            since = "0.1.0",
            value = "LLM auto-configuration properties are experimental while provider wiring conventions settle."
    )
    public enum LlmProvider {
        NONE,
        DEEPSEEK,
        OPENAI_COMPATIBLE
    }
}
