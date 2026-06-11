package com.actiongraph.planning;

import com.actiongraph.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public final class GoapPlanner implements Planner {
    public static final int DEFAULT_MAX_DEPTH = 32;
    public static final int DEFAULT_MAX_EXPANSIONS = 10_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(GoapPlanner.class);

    private final int maxDepth;
    private final int maxExpansions;

    public GoapPlanner() {
        this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_EXPANSIONS);
    }

    public GoapPlanner(int maxDepth, int maxExpansions) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }
        if (maxExpansions <= 0) {
            throw new IllegalArgumentException("maxExpansions must be > 0");
        }
        this.maxDepth = maxDepth;
        this.maxExpansions = maxExpansions;
    }

    @Override
    public Optional<Plan> plan(Goal goal, Set<Condition> currentState, Collection<Action> actions) {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(actions, "actions");

        Set<Condition> start = Set.copyOf(currentState);
        if (goal.isSatisfiedBy(start)) {
            LOGGER.debug("Goal '{}' already satisfied by current state", goal.name());
            return Optional.of(new Plan(List.of()));
        }

        List<Action> orderedActions = actions.stream()
                .sorted(Comparator.comparing(action -> action.id().value()))
                .toList();
        LOGGER.debug(
                "Planning goal '{}' from {} conditions with {} candidate actions, maxDepth={}, maxExpansions={}",
                goal.name(), start.size(), orderedActions.size(), maxDepth, maxExpansions);

        Queue<Node> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new Node(start, List.of(), 0));
        visited.add(stateKey(start));

        int expansions = 0;
        while (!queue.isEmpty() && expansions < maxExpansions) {
            Node node = queue.remove();
            if (node.depth >= maxDepth) {
                continue;
            }

            expansions++;
            for (Action action : orderedActions) {
                if (!node.state.containsAll(action.preconditions())) {
                    continue;
                }

                Set<Condition> nextState = applyEffects(node.state, action);
                String key = stateKey(nextState);
                if (!visited.add(key)) {
                    continue;
                }

                List<PlanStep> nextPath = new ArrayList<>(node.path);
                nextPath.add(new PlanStep(action.id()));
                if (goal.isSatisfiedBy(nextState)) {
                    LOGGER.debug(
                            "Plan found for goal '{}' with {} steps after {} expansions; final action={}",
                            goal.name(), nextPath.size(), expansions, action.id().value());
                    return Optional.of(new Plan(nextPath));
                }
                queue.add(new Node(Set.copyOf(nextState), List.copyOf(nextPath), node.depth + 1));
            }
        }

        LOGGER.debug(
                "No plan found for goal '{}' after {} expansions; visitedStates={}, queueRemaining={}",
                goal.name(), expansions, visited.size(), queue.size());
        return Optional.empty();
    }

    private Set<Condition> applyEffects(Set<Condition> state, Action action) {
        Set<Condition> next = new LinkedHashSet<>(state);
        next.addAll(action.effects());
        return Set.copyOf(next);
    }

    private String stateKey(Set<Condition> state) {
        return state.stream()
                .map(Condition::key)
                .sorted()
                .collect(Collectors.joining("\u001f"));
    }

    private record Node(Set<Condition> state, List<PlanStep> path, int depth) {
    }
}
