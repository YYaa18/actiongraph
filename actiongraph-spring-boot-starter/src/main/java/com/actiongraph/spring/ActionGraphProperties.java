package com.actiongraph.spring;

import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AmountLimitRule;
import com.actiongraph.runtime.GoapExecutor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "actiongraph")
public class ActionGraphProperties {
    private final PlannerProperties planner = new PlannerProperties();
    private final ExecutorProperties executor = new ExecutorProperties();
    private final ActionsProperties actions = new ActionsProperties();
    private final PersistenceProperties persistence = new PersistenceProperties();
    private final MaskingProperties masking = new MaskingProperties();
    private final HumanReviewProperties humanReview = new HumanReviewProperties();
    private final ConsoleProperties console = new ConsoleProperties();
    private final LimitsProperties limits = new LimitsProperties();

    public PlannerProperties getPlanner() {
        return planner;
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    public ActionsProperties getActions() {
        return actions;
    }

    public PersistenceProperties getPersistence() {
        return persistence;
    }

    public MaskingProperties getMasking() {
        return masking;
    }

    public HumanReviewProperties getHumanReview() {
        return humanReview;
    }

    public ConsoleProperties getConsole() {
        return console;
    }

    public LimitsProperties getLimits() {
        return limits;
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

    public static final class PersistenceProperties {
        private Duration suspendedRunClaimTimeout = Duration.ofMinutes(15);

        public Duration getSuspendedRunClaimTimeout() {
            return suspendedRunClaimTimeout;
        }

        public void setSuspendedRunClaimTimeout(Duration suspendedRunClaimTimeout) {
            Duration value = Objects.requireNonNull(suspendedRunClaimTimeout, "suspendedRunClaimTimeout");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("suspendedRunClaimTimeout must be positive");
            }
            this.suspendedRunClaimTimeout = value;
        }
    }

    public static final class MaskingProperties {
        private boolean enabled = false;
        private Set<String> blockedKeys = new LinkedHashSet<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getBlockedKeys() {
            return blockedKeys;
        }

        public void setBlockedKeys(Set<String> blockedKeys) {
            this.blockedKeys = blockedKeys == null ? new LinkedHashSet<>() : new LinkedHashSet<>(blockedKeys);
        }
    }

    public static final class HumanReviewProperties {
        private boolean riskBasedApprovalChain = false;
        private final CallbackEndpointProperties callbackEndpoint = new CallbackEndpointProperties();

        public boolean isRiskBasedApprovalChain() {
            return riskBasedApprovalChain;
        }

        public void setRiskBasedApprovalChain(boolean riskBasedApprovalChain) {
            this.riskBasedApprovalChain = riskBasedApprovalChain;
        }

        public CallbackEndpointProperties getCallbackEndpoint() {
            return callbackEndpoint;
        }
    }

    public static final class CallbackEndpointProperties {
        private boolean enabled = false;
        private String path = "/actiongraph/human-review/callbacks";
        private String tokenHeader = "X-ActionGraph-Review-Token";
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
                throw new IllegalArgumentException("callback endpoint path must not be blank");
            }
            this.path = path;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            if (tokenHeader == null || tokenHeader.isBlank()) {
                throw new IllegalArgumentException("callback endpoint token header must not be blank");
            }
            this.tokenHeader = tokenHeader;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            if (sharedSecret != null && sharedSecret.isBlank()) {
                throw new IllegalArgumentException("callback endpoint shared secret must not be blank");
            }
            this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
        }

        public boolean hasSharedSecret() {
            return !sharedSecret.isBlank();
        }
    }

    public static final class ConsoleProperties {
        private boolean enabled = false;
        private String path = "/actiongraph/console";
        private String tokenHeader = "X-ActionGraph-Console-Token";
        private String sharedSecret = "";
        private int defaultLimit = 50;
        private int maxLimit = 200;

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
                throw new IllegalArgumentException("console path must not be blank");
            }
            this.path = path;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            if (tokenHeader == null || tokenHeader.isBlank()) {
                throw new IllegalArgumentException("console token header must not be blank");
            }
            this.tokenHeader = tokenHeader;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            if (sharedSecret != null && sharedSecret.isBlank()) {
                throw new IllegalArgumentException("console shared secret must not be blank");
            }
            this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
        }

        public boolean hasSharedSecret() {
            return !sharedSecret.isBlank();
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            if (defaultLimit <= 0) {
                throw new IllegalArgumentException("console default limit must be positive");
            }
            this.defaultLimit = defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            if (maxLimit <= 0) {
                throw new IllegalArgumentException("console max limit must be positive");
            }
            this.maxLimit = maxLimit;
        }
    }

    public static final class LimitsProperties {
        private List<LimitRuleProperties> rules = new ArrayList<>();

        public List<LimitRuleProperties> getRules() {
            return rules;
        }

        public void setRules(List<LimitRuleProperties> rules) {
            this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
        }

        List<AmountLimitRule> toAmountLimitRules() {
            return rules.stream()
                    .map(LimitRuleProperties::toAmountLimitRule)
                    .toList();
        }
    }

    public static final class LimitRuleProperties {
        private String actionId = AmountLimitRule.ANY_ACTION;
        private String currency;
        private BigDecimal hardLimit;
        private BigDecimal reviewLimit;

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public BigDecimal getHardLimit() {
            return hardLimit;
        }

        public void setHardLimit(BigDecimal hardLimit) {
            this.hardLimit = hardLimit;
        }

        public BigDecimal getReviewLimit() {
            return reviewLimit;
        }

        public void setReviewLimit(BigDecimal reviewLimit) {
            this.reviewLimit = reviewLimit;
        }

        AmountLimitRule toAmountLimitRule() {
            return new AmountLimitRule(actionId, currency, hardLimit, reviewLimit);
        }
    }
}
