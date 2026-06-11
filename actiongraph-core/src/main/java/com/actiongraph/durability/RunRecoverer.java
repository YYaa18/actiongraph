package com.actiongraph.durability;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.api.Experimental;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure core recovery component for stale running checkpoints.
 *
 * <p>Scheduling, transactions, and deployment topology live outside core. This
 * class only claims one stale checkpoint and hands it back to the executor with
 * the configured recovery policy.
 */
@Experimental(
        since = "0.2.0",
        value = "Crash recovery is experimental until MS1 recovery pilots complete."
)
public final class RunRecoverer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRecoverer.class);

    private final GoapExecutor executor;
    private final SuspendedRunRepository repository;
    private final List<Action> actions;
    private final ActionRegistry registry;
    private final RecoveryPolicy recoveryPolicy;

    public RunRecoverer(
            GoapExecutor executor,
            SuspendedRunRepository repository,
            Collection<Action> actions,
            ActionRegistry registry,
            RecoveryPolicy recoveryPolicy
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        this.registry = Objects.requireNonNull(registry, "registry");
        this.recoveryPolicy = Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
    }

    /**
     * Claims and recovers at most one stale running checkpoint.
     *
     * @param staleBefore checkpoints older than this instant are stale
     * @return recovered run result when a checkpoint was claimed
     */
    public Optional<RunResult> recoverOne(Instant staleBefore) {
        Objects.requireNonNull(staleBefore, "staleBefore");
        Optional<SuspendedRun> claimed = repository.claimStaleRunning(staleBefore);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        SuspendedRun checkpoint = claimed.get();
        LOGGER.debug("Recovering stale running checkpoint: runId={}, policy={}, inFlightActionId={}",
                checkpoint.runId(),
                recoveryPolicy,
                checkpoint.inFlightActionId() == null ? "" : checkpoint.inFlightActionId().value());
        return Optional.of(executor.recover(checkpoint, actions, registry, recoveryPolicy));
    }

    /**
     * Claims and recovers up to {@code maxRuns} stale checkpoints.
     *
     * @param staleBefore checkpoints older than this instant are stale
     * @param maxRuns positive maximum number of checkpoints to recover
     * @return number of recovered checkpoints
     */
    public int recoverAvailable(Instant staleBefore, int maxRuns) {
        if (maxRuns <= 0) {
            throw new IllegalArgumentException("maxRuns must be positive");
        }
        int recovered = 0;
        while (recovered < maxRuns && recoverOne(staleBefore).isPresent()) {
            recovered++;
        }
        return recovered;
    }
}
