package com.actiongraph.spring;

import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.runtime.GoapExecutor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
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

        public boolean isRiskBasedApprovalChain() {
            return riskBasedApprovalChain;
        }

        public void setRiskBasedApprovalChain(boolean riskBasedApprovalChain) {
            this.riskBasedApprovalChain = riskBasedApprovalChain;
        }
    }
}
