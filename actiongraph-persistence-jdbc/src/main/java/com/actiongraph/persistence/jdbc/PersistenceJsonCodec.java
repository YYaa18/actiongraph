package com.actiongraph.persistence.jdbc;

import com.actiongraph.api.Internal;
import com.actiongraph.action.ActionId;
import com.actiongraph.exception.ActionGraphIntegrationException;
import com.actiongraph.identity.RunPrincipal;
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

@Internal
public final class PersistenceJsonCodec {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final BlackboardTypeRegistry blackboardTypeRegistry;

    public PersistenceJsonCodec() {
        this(defaultObjectMapper(), BlackboardTypeRegistry.allowAll());
    }

    public PersistenceJsonCodec(ObjectMapper objectMapper) {
        this(objectMapper, BlackboardTypeRegistry.allowAll());
    }

    public PersistenceJsonCodec(BlackboardTypeRegistry blackboardTypeRegistry) {
        this(defaultObjectMapper(), blackboardTypeRegistry);
    }

    public PersistenceJsonCodec(ObjectMapper objectMapper, BlackboardTypeRegistry blackboardTypeRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.blackboardTypeRegistry = Objects.requireNonNull(blackboardTypeRegistry, "blackboardTypeRegistry");
    }

    public static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    public String writeTraceData(Map<String, String> data) {
        return write(data == null ? Map.of() : data);
    }

    public Map<String, String> readTraceData(String json) {
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

    public String writeActionIds(List<ActionId> actionIds) {
        Objects.requireNonNull(actionIds, "actionIds");
        return write(actionIds.stream().map(ActionId::value).toList());
    }

    public List<ActionId> readActionIds(String json) {
        return read(json, STRING_LIST).stream()
                .map(ActionId::new)
                .toList();
    }

    public String writeConditions(Set<Condition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return write(conditions.stream().map(Condition::key).sorted().toList());
    }

    public Set<Condition> readConditions(String json) {
        return read(json, STRING_LIST).stream()
                .map(Condition::of)
                .collect(Collectors.toSet());
    }

    public String writeRunPrincipal(RunPrincipal principal) {
        RunPrincipal safe = principal == null ? RunPrincipal.anonymous() : principal;
        return write(new PrincipalSnapshot(
                safe.subject(),
                safe.clientId(),
                safe.delegationChain(),
                safe.attributes()
        ));
    }

    public RunPrincipal readRunPrincipal(String json) {
        if (json == null || json.isBlank()) {
            return RunPrincipal.anonymous();
        }
        PrincipalSnapshot snapshot = read(json, PrincipalSnapshot.class);
        return new RunPrincipal(
                snapshot.subject(),
                snapshot.clientId(),
                snapshot.delegationChain(),
                snapshot.attributes()
        );
    }

    private void putRestoredObject(InMemoryBlackboard blackboard, ObjectSnapshot snapshot) {
        try {
            blackboardTypeRegistry.verifyAllowed(snapshot.className());
            Class<?> type = Class.forName(snapshot.className());
            Object value = objectMapper.treeToValue(snapshot.value(), type);
            putTyped(blackboard, type, snapshot.id(), value);
        } catch (ClassNotFoundException ex) {
            throw new ActionGraphIntegrationException(
                    "Cannot restore blackboard object type: " + snapshot.className(), ex);
        } catch (JsonProcessingException ex) {
            throw new ActionGraphIntegrationException("Cannot restore blackboard object: " + snapshot.className(), ex);
        }
    }

    private <T> void putTyped(InMemoryBlackboard blackboard, Class<T> type, String id, Object value) {
        blackboard.put(BlackboardKey.of(type, id), type.cast(value));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ActionGraphIntegrationException("Cannot serialize runtime persistence payload", ex);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new ActionGraphIntegrationException("Cannot deserialize runtime persistence payload", ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new ActionGraphIntegrationException("Cannot deserialize runtime persistence payload", ex);
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

    private record PrincipalSnapshot(
            String subject,
            String clientId,
            List<String> delegationChain,
            Map<String, String> attributes
    ) {
        private PrincipalSnapshot {
            subject = subject == null || subject.isBlank() ? RunPrincipal.ANONYMOUS_SUBJECT : subject;
            clientId = clientId == null ? "" : clientId;
            delegationChain = delegationChain == null ? List.of() : List.copyOf(delegationChain);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
