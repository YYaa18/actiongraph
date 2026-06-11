package com.actiongraph.events;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.api.Experimental;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Claims expired event waits and routes them through timeout compensation.
 */
@Experimental(
        since = "0.2.0",
        value = "External event timeout sweeping is experimental until MS2 pilots complete."
)
public final class EventWaitSweeper {
    private final GoapExecutor executor;
    private final SuspendedRunRepository repository;
    private final Collection<Action> actions;
    private final ActionRegistry registry;

    public EventWaitSweeper(
            GoapExecutor executor,
            SuspendedRunRepository repository,
            Collection<Action> actions,
            ActionRegistry registry
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.actions = java.util.List.copyOf(Objects.requireNonNull(actions, "actions"));
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Optional<RunResult> sweepOne(Instant now) {
        Objects.requireNonNull(now, "now");
        Optional<SuspendedRun> waitingRun = repository.claimExpiredWaiting(now);
        return waitingRun.map(run -> executor.timeoutWaitingEvent(run, actions, registry));
    }

    public int sweepOnce(Instant now) {
        return sweepOne(now).isPresent() ? 1 : 0;
    }
}
