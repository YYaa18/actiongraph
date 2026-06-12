package com.actiongraph.events;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.runtime.SnapshotState;
import com.actiongraph.runtime.SuspendedRun;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.runtime.GoapExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivery boundary for events emitted by external systems.
 *
 * <p>The gateway owns the cross-cutting runtime semantics: atomic claim,
 * event-to-Blackboard application, RUNNING checkpoint writeback, and continuation
 * with the same run id and compensation stack.
 */
@Experimental(
        since = "0.2.0",
        value = "External event delivery is experimental until MS2 pilots complete."
)
public final class ExternalEventGateway {
    private final GoapExecutor executor;
    private final SuspendedRunRepository repository;
    private final Collection<Action> actions;
    private final ActionRegistry registry;
    private final Map<String, EventApplier> appliers;
    private final ConcurrentHashMap<EventKey, Boolean> deliveriesInFlight = new ConcurrentHashMap<>();

    public ExternalEventGateway(
            GoapExecutor executor,
            SuspendedRunRepository repository,
            Collection<Action> actions,
            ActionRegistry registry,
            Collection<EventApplier> appliers
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.actions = ListCopy.copyOf(Objects.requireNonNull(actions, "actions"));
        this.registry = Objects.requireNonNull(registry, "registry");
        this.appliers = applierMap(appliers);
    }

    public DeliveryResult deliver(String eventType, String correlationId, EventPayload payload) {
        return deliver(eventType, correlationId, payload, RunPrincipal.anonymous());
    }

    @Experimental(
            since = "0.2.0",
            value = "External event actor propagation is experimental until STD1 identity pilots settle."
    )
    public DeliveryResult deliver(String eventType, String correlationId, EventPayload payload, RunPrincipal actedBy) {
        String normalizedEventType = requireNonBlank(eventType, "eventType");
        String normalizedCorrelationId = requireNonBlank(correlationId, "correlationId");
        EventPayload normalizedPayload = payload == null ? EventPayload.empty() : payload;
        RunPrincipal safeActor = actedBy == null ? RunPrincipal.anonymous() : actedBy;
        EventApplier applier = appliers.get(normalizedEventType);
        if (applier == null) {
            return DeliveryResult.APPLIER_MISSING;
        }

        EventKey key = new EventKey(normalizedEventType, normalizedCorrelationId);
        if (deliveriesInFlight.putIfAbsent(key, Boolean.TRUE) != null) {
            return DeliveryResult.ALREADY_HANDLED;
        }

        Optional<SuspendedRun> claimed = Optional.empty();
        boolean checkpointWritten = false;
        try {
            claimed = repository.claimWaitingEvent(normalizedEventType, normalizedCorrelationId);
            if (claimed.isEmpty()) {
                return DeliveryResult.NOT_FOUND;
            }

            SuspendedRun waitingRun = claimed.get();
            applier.apply(normalizedPayload, waitingRun.blackboard());
            executor.recordEventDelivered(
                    waitingRun.runId(),
                    normalizedEventType,
                    normalizedCorrelationId,
                    normalizedPayload,
                    safeActor
            );
            SuspendedRun checkpoint = new SuspendedRun(
                    waitingRun.runId(),
                    waitingRun.goal(),
                    waitingRun.blackboard(),
                    waitingRun.executedActions(),
                    waitingRun.compensationStack(),
                    null,
                    "External event delivered",
                    SnapshotState.RUNNING,
                    Instant.now(),
                    null,
                    waitingRun.principal()
            );
            repository.saveCheckpoint(checkpoint);
            checkpointWritten = true;
            executor.resumeFromWaitingEvent(
                    checkpoint,
                    actions,
                    registry,
                    normalizedEventType,
                    normalizedCorrelationId,
                    normalizedPayload
            );
            return DeliveryResult.RESUMED;
        } catch (RuntimeException ex) {
            if (!checkpointWritten) {
                claimed.ifPresent(repository::save);
            }
            throw ex;
        } finally {
            deliveriesInFlight.remove(key);
        }
    }

    private static Map<String, EventApplier> applierMap(Collection<EventApplier> appliers) {
        Map<String, EventApplier> mapped = new LinkedHashMap<>();
        if (appliers != null) {
            for (EventApplier applier : appliers) {
                String eventType = requireNonBlank(applier.eventType(), "event applier eventType");
                EventApplier previous = mapped.putIfAbsent(eventType, applier);
                if (previous != null) {
                    throw new ActionGraphConfigurationException("Duplicate event applier for event type: " + eventType);
                }
            }
        }
        return Map.copyOf(mapped);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private record EventKey(String eventType, String correlationId) {
    }

    private static final class ListCopy {
        private static <T> Collection<T> copyOf(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
