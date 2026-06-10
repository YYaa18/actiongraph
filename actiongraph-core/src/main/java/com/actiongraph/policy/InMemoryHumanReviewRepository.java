package com.actiongraph.policy;

import com.actiongraph.action.ActionId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryHumanReviewRepository implements HumanReviewRepository {
    private final ConcurrentHashMap<Key, HumanReviewTask> tasks = new ConcurrentHashMap<>();

    @Override
    public void savePending(HumanReviewTask task) {
        Objects.requireNonNull(task, "task");
        if (task.decision() != HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("Only pending human review tasks can be saved as pending");
        }
        tasks.putIfAbsent(new Key(task.runId(), task.actionId()), task);
    }

    @Override
    public Optional<HumanReviewTask> find(String runId, ActionId actionId) {
        return Optional.ofNullable(tasks.get(new Key(runId, actionId)));
    }

    @Override
    public List<HumanReviewTask> findByRun(String runId) {
        return tasks.values().stream()
                .filter(task -> task.runId().equals(runId))
                .sorted(Comparator.comparing(task -> task.actionId().value()))
                .toList();
    }

    @Override
    public List<HumanReviewTask> findPending() {
        return tasks.values().stream()
                .filter(task -> task.decision() == HumanReviewDecision.PENDING)
                .sorted(Comparator.comparing(HumanReviewTask::createdAt))
                .toList();
    }

    @Override
    public void decide(
            String runId,
            ActionId actionId,
            HumanReviewDecision decision,
            String reviewer,
            String message
    ) {
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("Human review task decision must be APPROVED or DENIED");
        }
        Key key = new Key(runId, actionId);
        tasks.compute(key, (ignored, existing) -> {
            if (existing == null) {
                throw new IllegalStateException("No human review task found for " + runId + "/" + actionId.value());
            }
            return existing.withDecision(decision, reviewer, message, Instant.now());
        });
    }

    private record Key(String runId, ActionId actionId) {
        private Key {
            if (runId == null || runId.isBlank()) {
                throw new IllegalArgumentException("runId must not be blank");
            }
            Objects.requireNonNull(actionId, "actionId");
        }
    }
}
