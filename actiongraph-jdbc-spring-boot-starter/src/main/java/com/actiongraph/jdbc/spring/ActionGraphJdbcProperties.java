package com.actiongraph.jdbc.spring;

import com.actiongraph.persistence.jdbc.BlackboardTypeRegistry;
import com.actiongraph.persistence.jdbc.JdbcHumanReviewRepository;
import com.actiongraph.persistence.jdbc.JdbcMemoryRepository;
import com.actiongraph.persistence.jdbc.JdbcSuspendedRunRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "actiongraph.persistence.jdbc")
public class ActionGraphJdbcProperties {
    private boolean enabled = false;
    private Duration suspendedRunClaimTimeout = JdbcSuspendedRunRepository.DEFAULT_CLAIM_TIMEOUT;
    private final Tables tables = new Tables();
    private final Blackboard blackboard = new Blackboard();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public Tables getTables() {
        return tables;
    }

    public Blackboard getBlackboard() {
        return blackboard;
    }

    BlackboardTypeRegistry toBlackboardTypeRegistry() {
        if (blackboard.allowedClasses.isEmpty() && blackboard.allowedPackages.isEmpty()) {
            return BlackboardTypeRegistry.allowAll();
        }
        BlackboardTypeRegistry.Builder builder = BlackboardTypeRegistry.builder();
        blackboard.allowedClasses.forEach(builder::allowClassName);
        blackboard.allowedPackages.forEach(builder::allowPackage);
        return builder.build();
    }

    public static final class Tables {
        private String traceEvent = JdbcTraceRepository.DEFAULT_TABLE;
        private String suspendedRun = JdbcSuspendedRunRepository.DEFAULT_TABLE;
        private String humanReview = JdbcHumanReviewRepository.DEFAULT_TABLE;
        private String memory = JdbcMemoryRepository.DEFAULT_TABLE;

        public String getTraceEvent() {
            return traceEvent;
        }

        public void setTraceEvent(String traceEvent) {
            this.traceEvent = traceEvent;
        }

        public String getSuspendedRun() {
            return suspendedRun;
        }

        public void setSuspendedRun(String suspendedRun) {
            this.suspendedRun = suspendedRun;
        }

        public String getHumanReview() {
            return humanReview;
        }

        public void setHumanReview(String humanReview) {
            this.humanReview = humanReview;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }
    }

    public static final class Blackboard {
        private List<String> allowedClasses = new ArrayList<>();
        private List<String> allowedPackages = new ArrayList<>();

        public List<String> getAllowedClasses() {
            return allowedClasses;
        }

        public void setAllowedClasses(List<String> allowedClasses) {
            this.allowedClasses = allowedClasses == null ? new ArrayList<>() : new ArrayList<>(allowedClasses);
        }

        public List<String> getAllowedPackages() {
            return allowedPackages;
        }

        public void setAllowedPackages(List<String> allowedPackages) {
            this.allowedPackages = allowedPackages == null ? new ArrayList<>() : new ArrayList<>(allowedPackages);
        }
    }
}
