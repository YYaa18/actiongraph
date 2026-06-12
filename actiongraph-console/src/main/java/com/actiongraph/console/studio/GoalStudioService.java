package com.actiongraph.console.studio;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.fingerprint.ActionGraphFingerprints;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinition;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinitionFactory;
import com.actiongraph.interpretation.config.ConfiguredGoalParameter;
import com.actiongraph.planning.Condition;
import com.actiongraph.validation.ActionGraphValidator;
import com.actiongraph.validation.GoalValidation;
import com.actiongraph.validation.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Experimental(
        since = "0.2.0",
        value = "Goal Studio is experimental and intended for non-production drafting environments."
)
public final class GoalStudioService {
    private final GoalStudioLanguageModel languageModel;
    private final GoalCatalog catalog;
    private final ActionRegistry registry;
    private final ConfiguredGoalDefinitionFactory definitionFactory;
    private final int maxAutoRepairs;
    private final Path bundleDirectory;
    private final String sourceEnv;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StudioSession> sessions = new ConcurrentHashMap<>();

    public GoalStudioService(
            GoalStudioLanguageModel languageModel,
            GoalCatalog catalog,
            ActionRegistry registry,
            ConfiguredGoalDefinitionFactory definitionFactory,
            int maxAutoRepairs,
            Path bundleDirectory,
            String sourceEnv
    ) {
        this(languageModel, catalog, registry, definitionFactory, maxAutoRepairs,
                bundleDirectory, sourceEnv, Clock.systemUTC());
    }

    GoalStudioService(
            GoalStudioLanguageModel languageModel,
            GoalCatalog catalog,
            ActionRegistry registry,
            ConfiguredGoalDefinitionFactory definitionFactory,
            int maxAutoRepairs,
            Path bundleDirectory,
            String sourceEnv,
            Clock clock
    ) {
        if (maxAutoRepairs < 0) {
            throw new IllegalArgumentException("maxAutoRepairs must not be negative");
        }
        this.languageModel = Objects.requireNonNull(languageModel, "languageModel");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.definitionFactory = Objects.requireNonNull(definitionFactory, "definitionFactory");
        this.maxAutoRepairs = maxAutoRepairs;
        this.bundleDirectory = Objects.requireNonNull(bundleDirectory, "bundleDirectory");
        this.sourceEnv = sourceEnv == null || sourceEnv.isBlank() ? "test" : sourceEnv;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public GoalStudioSessionResponse createSession(String description) {
        String id = UUID.randomUUID().toString();
        StudioSession session = draft(id, requireText(description, "description"), "");
        sessions.put(id, session);
        return session.response();
    }

    public GoalStudioSessionResponse refine(String id, String feedback) {
        StudioSession previous = findSession(id);
        StudioSession refined = draft(previous.id(), previous.description(), requireText(feedback, "feedback"));
        sessions.put(previous.id(), refined);
        return refined.response();
    }

    public GoalStudioSessionResponse session(String id) {
        return findSession(id).response();
    }

    public GoalStudioSessionResponse approve(String id, String approver) {
        StudioSession session = findSession(id);
        if (!session.reachable()) {
            throw new ActionGraphConfigurationException("Cannot approve unreachable Goal Studio draft: " + id);
        }
        String approvedBy = requireText(approver, "approver");
        GoalDefinition definition = definitionFactory.definition(session.draft());
        String bundle = renderBundle(session.draft(), definition, approvedBy);
        String filename = safeFilename(session.draft().type()) + "-"
                + ActionGraphFingerprints.goal(definition).substring(0, 12) + ".yml";
        Path target = bundleDirectory.resolve(filename);
        try {
            Files.createDirectories(bundleDirectory);
            Files.writeString(target, bundle, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ActionGraphConfigurationException("Cannot write Goal Studio bundle: " + target, ex);
        }
        StudioSession approved = session.approved(target.toString(), bundle);
        sessions.put(id, approved);
        return approved.response();
    }

    private StudioSession draft(String id, String description, String feedback) {
        String userPrompt = userPrompt(description, feedback, "");
        DraftEvaluation last = null;
        for (int attempt = 0; attempt <= maxAutoRepairs; attempt++) {
            ConfiguredGoalDefinition draft = parseDraft(
                    languageModel.complete(systemPrompt(), userPrompt, 1200),
                    "studio session " + id
            );
            last = evaluate(draft);
            if (last.reachable()) {
                return new StudioSession(id, description, draft, last, attempt, null, null);
            }
            if (attempt < maxAutoRepairs) {
                userPrompt = userPrompt(description, feedback, last.diagnosticText());
            }
        }
        return new StudioSession(id, description, Objects.requireNonNull(last).draft(), last,
                maxAutoRepairs, null, null);
    }

    private DraftEvaluation evaluate(ConfiguredGoalDefinition draft) {
        GoalDefinition definition = definitionFactory.definition(draft);
        if (catalog.byType(definition.type()).isPresent()) {
            return DraftEvaluation.invalid(draft, List.of("goal type already exists: "
                    + definition.type().value()));
        }
        GoalCatalog preview = new GoalCatalog();
        catalog.all().forEach(preview::register);
        preview.register(definition);
        ValidationReport report = new ActionGraphValidator().validate(preview, registry.all());
        GoalValidation validation = report.goals().stream()
                .filter(goal -> goal.type().equals(definition.type()))
                .findFirst()
                .orElseThrow();
        if (!validation.reachable()) {
            return DraftEvaluation.invalid(draft, validation.diagnostics());
        }
        return DraftEvaluation.valid(draft,
                validation.previewPlan().stream().map(step -> step.actionId().value()).toList(),
                riskProfile(validation.previewPlan().stream().map(step -> step.actionId()).toList()));
    }

    private List<GoalStudioRiskItem> riskProfile(List<ActionId> actionIds) {
        List<GoalStudioRiskItem> risks = new ArrayList<>();
        for (ActionId actionId : actionIds) {
            Action action = registry.byId(actionId).orElseThrow();
            risks.add(new GoalStudioRiskItem(
                    action.id().value(),
                    action.description(),
                    action.riskLevel(),
                    action.requiresHumanReview(),
                    action.description().isBlank()
            ));
        }
        return List.copyOf(risks);
    }

    private String renderBundle(ConfiguredGoalDefinition draft, GoalDefinition definition, String approvedBy) {
        String actionGraphFingerprint = ActionGraphFingerprints.actionGraph(registry.all());
        String bundleFingerprint = ActionGraphFingerprints.goals(List.of(definition));
        StringBuilder yaml = new StringBuilder();
        yaml.append("actiongraph-bundle:\n");
        yaml.append("  bundle-fingerprint: ").append(quoted(bundleFingerprint)).append('\n');
        yaml.append("  action-graph-fingerprint: ").append(quoted(actionGraphFingerprint)).append('\n');
        yaml.append("  validated-at: ").append(quoted(Instant.now(clock).toString())).append('\n');
        yaml.append("  approved-by: ").append(quoted(approvedBy)).append('\n');
        yaml.append("  source-env: ").append(quoted(sourceEnv)).append('\n');
        yaml.append("  definitions:\n");
        appendDefinition(yaml, draft);
        return yaml.toString();
    }

    private void appendDefinition(StringBuilder yaml, ConfiguredGoalDefinition definition) {
        yaml.append("    - type: ").append(quoted(definition.type())).append('\n');
        yaml.append("      description: ").append(quoted(definition.description())).append('\n');
        yaml.append("      enabled: ").append(definition.enabled()).append('\n');
        yaml.append("      target-conditions: ").append(stringList(definition.targetConditions())).append('\n');
        yaml.append("      seed-conditions: ").append(stringList(definition.seedConditions())).append('\n');
        yaml.append("      parameters:\n");
        for (ConfiguredGoalParameter parameter : definition.parameters()) {
            yaml.append("        - name: ").append(quoted(parameter.name())).append('\n');
            yaml.append("          type: ").append(quoted(parameter.type())).append('\n');
            yaml.append("          required: ").append(parameter.required()).append('\n');
            yaml.append("          description: ").append(quoted(parameter.description())).append('\n');
            if (!parameter.example().isBlank()) {
                yaml.append("          example: ").append(quoted(parameter.example())).append('\n');
            }
        }
    }

    private ConfiguredGoalDefinition parseDraft(String text, String source) {
        JsonNode root = parseRoot(text);
        String type = requireString(root, "type");
        String description = optionalString(root, "description");
        List<String> targets = stringArray(firstPresent(root,
                "targetConditions", "target-conditions", "target_conditions", "target"));
        List<String> seeds = stringArray(firstPresent(root,
                "seedConditions", "seed-conditions", "seed_conditions", "seed"));
        List<ConfiguredGoalParameter> parameters = new ArrayList<>();
        JsonNode parameterArray = root.path("parameters");
        if (parameterArray.isArray()) {
            for (JsonNode item : parameterArray) {
                parameters.add(new ConfiguredGoalParameter(
                        requireString(item, "name"),
                        optionalString(item, "type", "string"),
                        optionalBoolean(item, "required", true),
                        optionalString(item, "description"),
                        optionalString(item, "example")
                ));
            }
        }
        return new ConfiguredGoalDefinition(type, description, true, targets, seeds, parameters, source);
    }

    private JsonNode parseRoot(String text) {
        String json = extractJsonObject(text);
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ActionGraphConfigurationException("Could not parse Goal Studio JSON output", ex);
        }
    }

    private String extractJsonObject(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ActionGraphConfigurationException("No JSON object found in Goal Studio output");
        }
        return trimmed.substring(start, end + 1);
    }

    private String systemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You draft ActionGraph Goal declarations for a deterministic GOAP runtime.
                Return valid JSON only. Do not return markdown, prose, plans, action order, or execution steps.
                Output fields are exactly: type, description, targetConditions, seedConditions, parameters.
                Parameters use: name, type, required, description, example.

                Available actions:
                """);
        for (Action action : registry.all().stream()
                .sorted(Comparator.comparing(action -> action.id().value()))
                .toList()) {
            prompt.append("- ").append(action.id().value())
                    .append(" | description=").append(action.description().isBlank() ? "<missing>" : action.description())
                    .append(" | preconditions=").append(conditions(action.preconditions()))
                    .append(" | effects=").append(conditions(action.effects()))
                    .append(" | riskLevel=").append(action.riskLevel())
                    .append(" | requiresHumanReview=").append(action.requiresHumanReview())
                    .append('\n');
        }
        prompt.append("Known conditions: ").append(allConditions()).append('\n');
        prompt.append("Existing goalTypes: ").append(catalog.all().stream()
                .map(goal -> goal.type().value())
                .sorted()
                .collect(Collectors.joining(", ")));
        return prompt.toString();
    }

    private String userPrompt(String description, String feedback, String diagnostics) {
        StringBuilder prompt = new StringBuilder("Requested capability: ").append(description);
        if (!feedback.isBlank()) {
            prompt.append("\nUser refinement: ").append(feedback);
        }
        if (!diagnostics.isBlank()) {
            prompt.append("\nValidation diagnostics to fix exactly:\n").append(diagnostics);
        }
        return prompt.toString();
    }

    private String allConditions() {
        Set<String> conditions = new LinkedHashSet<>();
        registry.all().forEach(action -> {
            action.preconditions().forEach(condition -> conditions.add(condition.key()));
            action.effects().forEach(condition -> conditions.add(condition.key()));
        });
        return conditions.stream().sorted().collect(Collectors.joining(", "));
    }

    private String conditions(Set<Condition> conditions) {
        return conditions.stream().map(Condition::key).sorted().collect(Collectors.joining(",", "[", "]"));
    }

    private StudioSession findSession(String id) {
        StudioSession session = sessions.get(requireText(id, "session id"));
        if (session == null) {
            throw new IllegalArgumentException("Goal Studio session not found: " + id);
        }
        return session;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String requireString(JsonNode root, String name) {
        String value = optionalString(root, name);
        if (value.isBlank()) {
            throw new ActionGraphConfigurationException("Expected non-blank string field: " + name);
        }
        return value;
    }

    private String optionalString(JsonNode root, String name) {
        return optionalString(root, name, "");
    }

    private String optionalString(JsonNode root, String name, String fallback) {
        JsonNode value = root.path(name);
        return value.isTextual() ? value.asText() : fallback;
    }

    private boolean optionalBoolean(JsonNode root, String name, boolean fallback) {
        JsonNode value = root.path(name);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    private JsonNode firstPresent(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return objectMapper.nullNode();
    }

    private List<String> stringArray(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new ActionGraphConfigurationException("Expected string array in Goal Studio draft");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new ActionGraphConfigurationException(
                        "Goal Studio draft arrays must contain non-blank strings");
            }
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private String quoted(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String stringList(List<String> values) {
        return values.stream().map(this::quoted).collect(Collectors.joining(", ", "[", "]"));
    }

    private String safeFilename(String value) {
        String safe = value.chars()
                .mapToObj(ch -> Character.isLetterOrDigit(ch) || ch == '-' || ch == '_'
                        ? Character.toString((char) ch)
                        : "-")
                .collect(Collectors.joining())
                .replaceAll("-+", "-");
        return safe.isBlank() ? "goal" : safe;
    }

    private record DraftEvaluation(
            ConfiguredGoalDefinition draft,
            boolean reachable,
            List<String> diagnostics,
            List<String> previewPlan,
            List<GoalStudioRiskItem> riskProfile
    ) {
        static DraftEvaluation valid(
                ConfiguredGoalDefinition draft,
                List<String> previewPlan,
                List<GoalStudioRiskItem> riskProfile
        ) {
            return new DraftEvaluation(draft, true, List.of(), previewPlan, riskProfile);
        }

        static DraftEvaluation invalid(ConfiguredGoalDefinition draft, List<String> diagnostics) {
            return new DraftEvaluation(draft, false, diagnostics, List.of(), List.of());
        }

        String diagnosticText() {
            return String.join(System.lineSeparator(), diagnostics);
        }
    }

    private record StudioSession(
            String id,
            String description,
            ConfiguredGoalDefinition draft,
            DraftEvaluation evaluation,
            int repairAttempts,
            String bundlePath,
            String bundle
    ) {
        boolean reachable() {
            return evaluation.reachable();
        }

        StudioSession approved(String bundlePath, String bundle) {
            return new StudioSession(id, description, draft, evaluation, repairAttempts, bundlePath, bundle);
        }

        GoalStudioSessionResponse response() {
            GoalStudioStatus status = bundlePath == null
                    ? (evaluation.reachable() ? GoalStudioStatus.DRAFT_VALID : GoalStudioStatus.DRAFT_INVALID)
                    : GoalStudioStatus.APPROVED;
            return new GoalStudioSessionResponse(
                    id,
                    status,
                    description,
                    draft,
                    evaluation.reachable(),
                    evaluation.diagnostics(),
                    evaluation.previewPlan(),
                    evaluation.riskProfile(),
                    repairAttempts,
                    bundlePath,
                    bundle
            );
        }
    }
}
