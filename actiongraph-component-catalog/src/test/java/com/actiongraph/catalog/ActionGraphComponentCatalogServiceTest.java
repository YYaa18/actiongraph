package com.actiongraph.catalog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphComponentCatalogServiceTest {
    @Test
    void defaultCatalogContainsComposableFrameworkAndControlPlaneModules() {
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        assertThat(service.components())
                .extracting(ActionGraphComponent::module)
                .contains(
                        "actiongraph-core",
                        "actiongraph-runtime-api",
                        "actiongraph-human-review",
                        "actiongraph-control-plane-spring-boot-starter",
                        "actiongraph-component-catalog",
                        "actiongraph-control-plane-api",
                        "actiongraph-component-catalog-spring-boot-starter",
                        "actiongraph-console",
                        "actiongraph-console-spring-boot-starter"
                );
        assertThat(service.components())
                .filteredOn(component -> component.kind() == ComponentKind.CONTROL_PLANE)
                .extracting(ActionGraphComponent::module)
                .contains(
                        "actiongraph-runtime-api-spring-boot-starter",
                        "actiongraph-human-review-api-spring-boot-starter",
                        "actiongraph-console-spring-boot-starter",
                        "actiongraph-component-catalog-spring-boot-starter"
                );
        assertThat(service.component("actiongraph-control-plane-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.requires())
                        .contains("actiongraph-component-catalog-spring-boot-starter"));
        assertThat(service.component("actiongraph-runtime-api-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.requires())
                            .containsExactly("actiongraph-runtime-api", "actiongraph-control-plane-api");
                    assertThat(component.capabilities())
                            .contains("runtime-http-api", "runtime-request-trace-metadata");
                });
        assertThat(service.component("actiongraph-runtime-api"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.capabilities())
                        .contains("runtime-entry-service", "runtime-request-trace-metadata"));
        assertThat(service.component("actiongraph-component-catalog-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.requires())
                        .containsExactly("actiongraph-component-catalog", "actiongraph-control-plane-api"));
        assertThat(service.component("actiongraph-control-plane-api"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.compatibility()).isEqualTo(ComponentCompatibility.JAVA8_CLIENT.label());
                    assertThat(component.capabilities())
                            .contains("java8-control-plane-client", "java8-runtime-client",
                                    "java8-component-catalog-client",
                                    "java8-human-review-client", "java8-console-client", "http-audit-headers",
                                    "shared-secret-token-verification");
                });
        assertThat(service.component("actiongraph-component-catalog"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.compatibility()).isEqualTo(ComponentCompatibility.JAVA8_CLIENT.label());
                    assertThat(component.capabilities())
                            .contains("component-catalog", "composition-profiles");
                });
        assertThat(service.component("actiongraph-core"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.compatibility())
                        .isEqualTo(ComponentCompatibility.JAVA21_PLUS.label()));
        assertThat(service.component("actiongraph-samples"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.compatibility())
                        .isEqualTo(ComponentCompatibility.SAMPLE_ONLY.label()));
        assertThat(service.component("actiongraph-bom"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.compatibility())
                        .isEqualTo(ComponentCompatibility.NO_RUNTIME_CODE.label()));
        assertThat(service.componentsByCompatibility(ComponentCompatibility.JAVA8_CLIENT.label()))
                .extracting(ActionGraphComponent::module)
                .containsExactly("actiongraph-component-catalog", "actiongraph-control-plane-api");
        assertThat(service.componentsByCompatibility(ComponentCompatibility.JAVA8_RUNTIME.label()))
                .isEmpty();
        assertThat(service.componentsByCompatibility(null)).isEmpty();
        assertThat(service.components())
                .allSatisfy(component -> assertThat(component.compatibility()).isNotBlank());
        assertThat(service.component("actiongraph-control-plane" + "-auth"))
                .isEmpty();
        assertThat(service.component("actiongraph-console-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.requires())
                            .containsExactly("actiongraph-console", "actiongraph-control-plane-api");
                    assertThat(component.capabilities())
                            .contains("console-json-http-api", "console-html-ui", "console-export-http-api");
                });
        assertThat(service.component("actiongraph-memory-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.requires())
                            .containsExactly("actiongraph-memory", "actiongraph-memory-jdbc",
                                    "actiongraph-jdbc-spring-boot-starter");
                    assertThat(component.capabilities())
                            .contains("spring-memory-autoconfiguration", "spring-jdbc-memory-repository");
                });
        assertThat(service.component("actiongraph-memory-jdbc" + "-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-human-review-api-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.requires())
                            .containsExactly("actiongraph-human-review", "actiongraph-control-plane-api");
                    assertThat(component.capabilities())
                            .contains("human-review-http-api", "human-review-callback-http-api");
                });
    }

    @Test
    void defaultCatalogExposesCompositionProfiles() {
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        assertThat(service.profile("full-control-plane"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .contains("actiongraph-control-plane-spring-boot-starter"));
        assertThat(service.profile("ecosystem-introspection"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .contains("actiongraph-control-plane-api",
                                "actiongraph-component-catalog-spring-boot-starter"));
        assertThat(service.profile("control-plane-response-contracts"))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.modules())
                            .containsExactly("actiongraph-control-plane-api");
                    assertThat(profile.notes())
                            .anySatisfy(note -> assertThat(note)
                                    .contains("ActionGraphControlPlaneHttpClient"));
                    assertThat(profile.notes())
                            .anySatisfy(note -> assertThat(note)
                                    .contains("ActionGraphConsoleHttpClient"));
                });
        assertThat(service.profile("java8-legacy-client"))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.modules())
                            .containsExactly("actiongraph-component-catalog", "actiongraph-control-plane-api");
                    assertThat(profile.notes())
                            .anySatisfy(note -> assertThat(note).contains("console endpoints"));
                });
        assertThat(service.profile("control-plane-shared" + "-auth"))
                .isEmpty();
        assertThat(service.profile("console-control-plane"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .containsExactly("actiongraph-console", "actiongraph-console-spring-boot-starter"));
        assertThat(service.profile("human-review-control-plane"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .containsExactly("actiongraph-human-review", "actiongraph-human-review-api-spring-boot-starter"));
        assertThat(service.profilesContainingModule("actiongraph-control-plane-api"))
                .extracting(ActionGraphCompositionProfile::name)
                .contains("control-plane-response-contracts", "ecosystem-introspection", "java8-legacy-client");
        assertThat(service.profilesContainingModule("actiongraph-console"))
                .extracting(ActionGraphCompositionProfile::name)
                .contains("console-control-plane");
        assertThat(service.profilesContainingModule("actiongraph-control-plane-spring-boot-starter"))
                .extracting(ActionGraphCompositionProfile::name)
                .contains("full-control-plane", "full-pilot-service");
        assertThat(service.profilesContainingModule("missing")).isEmpty();
        assertThat(service.profilesContainingModule(" ")).isEmpty();
    }

    @Test
    void defaultCatalogStaysAlignedWithGradleModuleSurface() throws IOException {
        Path root = repositoryRoot();
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        Set<String> includedModules = parseModules(root.resolve("settings.gradle.kts"),
                "include\\(\"([^\"]+)\"\\)");
        Set<String> catalogModules = service.components().stream()
                .map(ActionGraphComponent::module)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(catalogModules).containsExactlyInAnyOrderElementsOf(includedModules);

        Set<String> bomModules = parseModules(root.resolve("actiongraph-bom/build.gradle.kts"),
                "api\\(project\\(\":([^\"]+)\"\\)\\)");
        Set<String> publishableLibraryModules = service.components().stream()
                .filter(component -> component.kind() != ComponentKind.SAMPLE)
                .map(ActionGraphComponent::module)
                .filter(module -> !module.equals("actiongraph-bom"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(bomModules).containsExactlyInAnyOrderElementsOf(publishableLibraryModules);
    }

    @Test
    void documentedActionGraphModuleReferencesResolveToCatalogEntries() throws IOException {
        Path root = repositoryRoot();
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();
        Set<String> catalogModules = service.components().stream()
                .map(ActionGraphComponent::module)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> documentedModules = new LinkedHashSet<>();
        for (Path document : documentationFiles(root)) {
            documentedModules.addAll(parseModules(document,
                    "(?<![A-Za-z0-9_./-])(actiongraph-[a-z0-9]+(?:-[a-z0-9]+)*)(?![A-Za-z0-9_./-])"));
        }

        assertThat(documentedModules)
                .as("documented ActionGraph artifact names should be real catalog modules")
                .isSubsetOf(catalogModules);
    }

    @Test
    void compatibilityLabelsAreFromClosedSet() {
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();
        Set<String> validLabels = Arrays.stream(ComponentCompatibility.values())
                .map(ComponentCompatibility::label)
                .collect(Collectors.toSet());

        assertThat(service.components())
                .allSatisfy(component -> assertThat(validLabels).contains(component.compatibility()));
    }

    @Test
    void java8ClientCatalogMatchesBuildGateAndMavenConsumerExample() throws IOException {
        Path root = repositoryRoot();
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        Set<String> catalogJava8Modules = service.componentsByCompatibility(ComponentCompatibility.JAVA8_CLIENT.label())
                .stream()
                .map(ActionGraphComponent::module)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> buildJava8Modules = parseJava8CompatibleModules(root.resolve("build.gradle.kts"));
        Set<String> mavenConsumerModules = parseModules(root.resolve("docs/examples/java8-maven-consumer/pom.xml"),
                "<artifactId>(actiongraph-[^<]+)</artifactId>");
        mavenConsumerModules.remove("actiongraph-java8-maven-consumer");
        mavenConsumerModules.remove("actiongraph-bom");

        assertThat(catalogJava8Modules)
                .as("catalog java8-client labels must match modules compiled and verified as Java 8 artifacts")
                .containsExactlyInAnyOrderElementsOf(buildJava8Modules);
        assertThat(mavenConsumerModules)
                .as("Maven Java 8 consumer example should import exactly the Java 8 client artifacts")
                .containsExactlyInAnyOrderElementsOf(catalogJava8Modules);
    }

    @Test
    void lookupReturnsEmptyForUnknownEntries() {
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        assertThat(service.component("missing")).isEmpty();
        assertThat(service.profile("missing")).isEmpty();
    }

    @Test
    void catalogRejectsDuplicateModulesAndProfiles() {
        ActionGraphComponent component = new ActionGraphComponent(
                "module-a",
                ComponentKind.CORE,
                "A module",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );
        ActionGraphCompositionProfile profile = new ActionGraphCompositionProfile(
                "profile-a",
                "A profile",
                java.util.List.of("module-a"),
                java.util.List.of()
        );

        assertThatThrownBy(() -> new ActionGraphComponentCatalog(
                java.util.List.of(component, component),
                java.util.List.of(profile)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate components");

        assertThatThrownBy(() -> new ActionGraphComponentCatalog(
                java.util.List.of(component),
                java.util.List.of(profile, profile)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate profiles");
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("actiongraph-component-catalog"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private Set<String> parseModules(Path file, String regex) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        var matcher = Pattern.compile(regex).matcher(content);
        Set<String> modules = new LinkedHashSet<>();
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        return modules;
    }

    private java.util.List<Path> documentationFiles(Path root) throws IOException {
        java.util.List<Path> documents = new java.util.ArrayList<>();
        documents.add(root.resolve("README.md"));
        documents.add(root.resolve("README.zh-CN.md"));
        try (var paths = Files.walk(root.resolve("docs"))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith(".md") || fileName.endsWith(".html");
                    })
                    .sorted()
                    .forEach(documents::add);
        }
        return documents;
    }

    private Set<String> parseJava8CompatibleModules(Path buildFile) throws IOException {
        String content = Files.readString(buildFile, StandardCharsets.UTF_8);
        var matcher = Pattern.compile("val\\s+java8CompatibleModules\\s*=\\s*setOf\\((.*?)\\)",
                Pattern.DOTALL).matcher(content);
        assertThat(matcher.find())
                .as("root build must declare java8CompatibleModules")
                .isTrue();

        Set<String> modules = new LinkedHashSet<>();
        var moduleMatcher = Pattern.compile("\"(actiongraph-[^\"]+)\"").matcher(matcher.group(1));
        while (moduleMatcher.find()) {
            modules.add(moduleMatcher.group(1));
        }
        return modules;
    }
}
