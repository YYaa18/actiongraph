package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.ApprovalStage;
import com.actiongraph.policy.StageDecision;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class PersistenceJsonCodec {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final BlackboardTypeRegistry blackboardTypeRegistry;

    PersistenceJsonCodec() {
        this(defaultObjectMapper(), BlackboardTypeRegistry.allowAll());
    }

    PersistenceJsonCodec(ObjectMapper objectMapper) {
        this(objectMapper, BlackboardTypeRegistry.allowAll());
    }

    PersistenceJsonCodec(BlackboardTypeRegistry blackboardTypeRegistry) {
        this(defaultObjectMapper(), blackboardTypeRegistry);
    }

    PersistenceJsonCodec(ObjectMapper objectMapper, BlackboardTypeRegistry blackboardTypeRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.blackboardTypeRegistry = Objects.requireNonNull(blackboardTypeRegistry, "blackboardTypeRegistry");
    }

    static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    String writeTraceData(Map<String, String> data) {
        return write(data == null ? Map.of() : data);
    }

    Map<String, String> readTraceData(String json) {
        return read(json, STRING_MAP);
    }

    String writeGoal(Goal goal) {
        Objects.requireNonNull(goal, "goal");
        return write(new GoalSnapshot(
                goal.name(),
                goal.targetConditions().stream()
                        .map(Condition::key)
                        .sorted()
                        .toList()
        ));
    }

    Goal readGoal(String json) {
        GoalSnapshot snapshot = read(json, GoalSnapshot.class);
        return new Goal(
                snapshot.name(),
                snapshot.targetConditions().stream()
                        .map(Condition::of)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    String writeBlackboard(Blackboard blackboard) {
        Objects.requireNonNull(blackboard, "blackboard");
        List<ObjectSnapshot> objects = blackboard.snapshotEntries().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().type().getName() + "#" + entry.getKey().id()))
                .map(entry -> new ObjectSnapshot(
                        entry.getKey().type().getName(),
                        entry.getKey().id(),
                        objectMapper.valueToTree(entry.getValue())
                ))
                .toList();
        return write(new BlackboardSnapshot(
                blackboard.conditions().stream()
                        .map(Condition::key)
                        .sorted()
                        .toList(),
                objects
        ));
    }

    Blackboard readBlackboard(String json) {
        BlackboardSnapshot snapshot = read(json, BlackboardSnapshot.class);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        snapshot.objects().forEach(object -> putRestoredObject(blackboard, object));
        snapshot.conditions().stream()
                .map(Condition::of)
                .forEach(blackboard::addCondition);
        return blackboard;
    }

    String writeActionIds(List<ActionId> actionIds) {
        Objects.requireNonNull(actionIds, "actionIds");
        return write(actionIds.stream().map(ActionId::value).toList());
    }

    List<ActionId> readActionIds(String json) {
        return read(json, STRING_LIST).stream()
                .map(ActionId::new)
                .toList();
    }

    String writeApprovalStages(List<ApprovalStage> stages) {
        Objects.requireNonNull(stages, "stages");
        return write(stages);
    }

    List<ApprovalStage> readApprovalStages(String json) {
        return read(json, new TypeReference<List<ApprovalStage>>() {
        });
    }

    String writeStageDecisions(List<StageDecision> decisions) {
        Objects.requireNonNull(decisions, "decisions");
        return write(decisions);
    }

    List<StageDecision> readStageDecisions(String json) {
        return read(json, new TypeReference<List<StageDecision>>() {
        });
    }

    String writeConditions(Set<Condition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return write(conditions.stream().map(Condition::key).sorted().toList());
    }

    Set<Condition> readConditions(String json) {
        return read(json, STRING_LIST).stream()
                .map(Condition::of)
                .collect(Collectors.toSet());
    }

    private void putRestoredObject(InMemoryBlackboard blackboard, ObjectSnapshot snapshot) {
        try {
            blackboardTypeRegistry.verifyAllowed(snapshot.className());
            Class<?> type = Class.forName(snapshot.className());
            Object value = objectMapper.treeToValue(snapshot.value(), type);
            putTyped(blackboard, type, snapshot.id(), value);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Cannot restore blackboard object type: " + snapshot.className(), ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot restore blackboard object: " + snapshot.className(), ex);
        }
    }

    private <T> void putTyped(InMemoryBlackboard blackboard, Class<T> type, String id, Object value) {
        blackboard.put(BlackboardKey.of(type, id), type.cast(value));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize runtime persistence payload", ex);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot deserialize runtime persistence payload", ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot deserialize runtime persistence payload", ex);
        }
    }

    private record GoalSnapshot(String name, List<String> targetConditions) {
    }

    private record BlackboardSnapshot(List<String> conditions, List<ObjectSnapshot> objects) {
    }

    private record ObjectSnapshot(String className, String id, JsonNode value) {
        private ObjectSnapshot {
            id = id == null ? BlackboardKey.DEFAULT_ID : id;
        }
    }
}
