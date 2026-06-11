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
                        "actiongraph-human-review",
                        "actiongraph-control-plane-api",
                        "actiongraph-console",
                        "actiongraph-spring-boot-starter"
                );
        assertThat(service.components())
                .filteredOn(component -> component.kind() == ComponentKind.CONTROL_PLANE)
                .extracting(ActionGraphComponent::module)
                .contains(
                        "actiongraph-control-plane-api",
                        "actiongraph-console"
                );
        assertThat(service.component("actiongraph-control-plane-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.requires())
                            .containsExactly("actiongraph-core", "actiongraph-console",
                                    "actiongraph-control-plane-api",
                                    "actiongraph-governance", "actiongraph-human-review",
                                    "actiongraph-persistence-jdbc");
                    assertThat(component.capabilities())
                            .contains("spring-runtime-autoconfiguration", "action-scanning",
                                    "spring-jdbc-runtime-repositories", "spring-memory-autoconfiguration",
                                    "spring-human-review-policy", "spring-governance-autoconfiguration",
                                    "runtime-http-api", "runtime-request-trace-metadata",
                                    "component-catalog-http-api", "human-review-http-api",
                                    "human-review-callback-http-api", "console-json-http-api",
                                    "console-html-ui", "console-export-http-api",
                                    "spring-console-jdbc-repository");
                });
        assertThat(service.component("actiongraph-runtime-api"))
                .isEmpty();
        assertThat(service.component("actiongraph-interpretation"))
                .isEmpty();
        assertThat(service.component("actiongraph-llm"))
                .isEmpty();
        assertThat(service.component("actiongraph-annotations"))
                .isEmpty();
        assertThat(service.component("actiongraph-core"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.capabilities())
                        .contains("runtime-entry-service", "runtime-request-trace-metadata",
                                "goal-interpretation", "blackboard-seeding",
                                "structured-memory", "context-loading",
                                "annotation-actions", "reflection-adapter"));
        assertThat(service.component("actiongraph-component-catalog-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-control-plane-api"))
                .isPresent()
                .get()
                .satisfies(component -> {
                    assertThat(component.compatibility()).isEqualTo(ComponentCompatibility.JAVA8_CLIENT.label());
                    assertThat(component.capabilities())
                            .contains("component-catalog", "composition-profiles",
                                    "java8-control-plane-client", "java8-runtime-client",
                                    "java8-component-catalog-client",
                                    "java8-human-review-client", "java8-console-client", "http-audit-headers",
                                    "shared-secret-token-verification");
                });
        assertThat(service.component("actiongraph-component-catalog"))
                .isEmpty();
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
                .containsExactly("actiongraph-control-plane-api");
        assertThat(service.componentsByCompatibility(ComponentCompatibility.JAVA8_RUNTIME.label()))
                .isEmpty();
        assertThat(service.componentsByCompatibility(null)).isEmpty();
        assertThat(service.components())
                .allSatisfy(component -> assertThat(component.compatibility()).isNotBlank());
        assertThat(service.component("actiongraph-control-plane" + "-auth"))
                .isEmpty();
        assertThat(service.component("actiongraph-console-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-memory-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-memory"))
                .isEmpty();
        assertThat(service.component("actiongraph-memory-jdbc" + "-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-memory-jdbc"))
                .isEmpty();
        assertThat(service.component("actiongraph-human-review-jdbc"))
                .isEmpty();
        assertThat(service.component("actiongraph-governance-human-review"))
                .isEmpty();
        assertThat(service.component("actiongraph-human-review-api-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-runtime-api-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-jdbc-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-human-review-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-governance-spring-boot-starter"))
                .isEmpty();
        assertThat(service.component("actiongraph-governance-human-review-spring-boot-starter"))
                .isEmpty();
    }

    @Test
    void defaultCatalogExposesCompositionProfiles() {
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        assertThat(service.profile("full-control-plane"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .containsExactly(
                                "actiongraph-spring-boot-starter"));
        assertThat(service.profile("ecosystem-introspection"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .contains("actiongraph-control-plane-api",
                                "actiongraph-spring-boot-starter"));
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
                            .containsExactly("actiongraph-control-plane-api");
                    assertThat(profile.notes())
                            .anySatisfy(note -> assertThat(note).contains("component-catalog"));
                });
        assertThat(service.profile("control-plane-shared" + "-auth"))
                .isEmpty();
        assertThat(service.profile("console-control-plane"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .containsExactly("actiongraph-console", "actiongraph-spring-boot-starter"));
        assertThat(service.profile("human-review-control-plane"))
                .isPresent()
                .get()
                .satisfies(profile -> assertThat(profile.modules())
                        .containsExactly("actiongraph-human-review", "actiongraph-spring-boot-starter"));
        assertThat(service.profilesContainingModule("actiongraph-control-plane-api"))
                .extracting(ActionGraphCompositionProfile::name)
                .contains("control-plane-response-contracts", "ecosystem-introspection", "java8-legacy-client");
        assertThat(service.profilesContainingModule("actiongraph-console"))
                .extracting(ActionGraphCompositionProfile::name)
                .contains("console-control-plane");
        assertThat(service.profilesContainingModule("actiongraph-spring-boot-starter"))
                .extracting(ActionGraphCompositionProfile::name)
                .contains("spring-business-runtime", "durable-spring-runtime", "runtime-entry-api",
                        "full-control-plane", "full-pilot-service", "console-control-plane");
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
    void gradleModulesRequireModuleGovernanceApproval() throws IOException {
        Path root = repositoryRoot();
        Set<String> includedModules = parseModules(root.resolve("settings.gradle.kts"),
                "include\\(\"([^\"]+)\"\\)");
        Set<String> governedModules = parseGovernedModules(
                root.resolve("docs/frameworkization/module-governance.md"));

        assertThat(governedModules)
                .as("every included module should be listed in the module governance approval ledger")
                .containsExactlyInAnyOrderElementsOf(includedModules);
    }

    @Test
    void catalogRequiredModulesMatchGradleProjectDependencies() throws IOException {
        Path root = repositoryRoot();
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();

        service.components().stream()
                .filter(component -> component.kind() != ComponentKind.PLATFORM)
                .filter(component -> component.kind() != ComponentKind.SAMPLE)
                .forEach(component -> {
                    try {
                        Set<String> gradleDependencies = parseModules(
                                root.resolve(component.module()).resolve("build.gradle.kts"),
                                "(?:api|implementation|compileOnly|runtimeOnly)\\(project\\(\":([^\"]+)\"\\)\\)"
                        );
                        assertThat(component.requires())
                                .as(component.module()
                                        + " catalog requires should match direct Gradle project dependencies")
                                .containsExactlyInAnyOrderElementsOf(gradleDependencies);
                    } catch (IOException ex) {
                        throw new IllegalStateException(
                                "Cannot inspect Gradle dependencies for " + component.module(), ex);
                    }
                });
    }

    @Test
    void catalogRelationshipsReferenceExistingModules() {
        ActionGraphComponentCatalogService service = ActionGraphComponentCatalogService.defaultCatalog();
        Set<String> catalogModules = service.components().stream()
                .map(ActionGraphComponent::module)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        service.components().forEach(component -> {
            assertThat(component.requires())
                    .as(component.module() + " requires should reference catalog modules")
                    .isSubsetOf(catalogModules);
            assertThat(component.optionalWith())
                    .as(component.module() + " optionalWith should reference catalog modules")
                    .isSubsetOf(catalogModules);
        });
        service.profiles().forEach(profile -> assertThat(profile.modules())
                .as(profile.name() + " profile modules should reference catalog modules")
                .isSubsetOf(catalogModules));
    }

    @Test
    void springBootStarterAutoConfigurationImportsMatchAnnotatedClasses() throws IOException {
        Path root = repositoryRoot();
        for (Path starter : springBootStarterDirectories(root)) {
            Set<String> annotatedAutoConfigurations = annotatedAutoConfigurationClasses(starter);
            Set<String> importedAutoConfigurations = autoConfigurationImports(starter);

            assertThat(importedAutoConfigurations)
                    .as(starter.getFileName()
                            + " AutoConfiguration.imports should match @AutoConfiguration classes")
                    .containsExactlyInAnyOrderElementsOf(annotatedAutoConfigurations);
        }
    }

    @Test
    void springBootStarterConfigurationPropertiesAreEnabled() throws IOException {
        Path root = repositoryRoot();
        for (Path starter : springBootStarterDirectories(root)) {
            Set<String> propertiesClasses = configurationPropertiesClasses(starter).stream()
                    .map(this::simpleClassName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> enabledPropertiesClasses = enabledConfigurationPropertiesClasses(starter);

            assertThat(enabledPropertiesClasses)
                    .as(starter.getFileName()
                            + " should enable every @ConfigurationProperties class it defines")
                    .containsAll(propertiesClasses);
        }
    }

    @Test
    void springBootStarterWebAutoConfigurationsRemainPropertyGated() throws IOException {
        Path root = repositoryRoot();
        for (Path starter : springBootStarterDirectories(root)) {
            for (Path sourceFile : javaSourceFiles(starter)) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                if (source.contains("@AutoConfiguration")
                        && source.contains("@ConditionalOnWebApplication")) {
                    assertThat(source)
                            .as(sourceFile + " should require an explicit enabled=true property")
                            .contains("@ConditionalOnProperty");
                }
            }
        }
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
        Path mavenConsumerPom = root.resolve("docs/examples/java8-maven-consumer/pom.xml");
        String mavenConsumerPomXml = Files.readString(mavenConsumerPom, StandardCharsets.UTF_8);
        Set<String> mavenConsumerModules = parseModules(mavenConsumerPom,
                "<artifactId>(actiongraph-[^<]+)</artifactId>");
        mavenConsumerModules.remove("actiongraph-java8-maven-consumer");
        mavenConsumerModules.remove("actiongraph-bom");

        assertThat(catalogJava8Modules)
                .as("catalog java8-client labels must match modules compiled and verified as Java 8 artifacts")
                .containsExactlyInAnyOrderElementsOf(buildJava8Modules);
        assertThat(mavenConsumerModules)
                .as("Maven Java 8 consumer example should import exactly the Java 8 client artifacts")
                .containsExactlyInAnyOrderElementsOf(catalogJava8Modules);
        assertThat(mavenConsumerPomXml)
                .as("Maven Java 8 consumer must build on a real JDK 8, where --release is unavailable")
                .doesNotContain("maven.compiler.release", "<release>")
                .contains("<maven.compiler.source>1.8</maven.compiler.source>")
                .contains("<maven.compiler.target>1.8</maven.compiler.target>");
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
                    && Files.isDirectory(current.resolve("actiongraph-control-plane-api"))) {
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

    private Set<String> parseGovernedModules(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String startMarker = "<!-- module-governance:start -->";
        String endMarker = "<!-- module-governance:end -->";
        int start = content.indexOf(startMarker);
        int end = content.indexOf(endMarker);
        assertThat(start)
                .as(file + " should contain a module governance start marker")
                .isGreaterThanOrEqualTo(0);
        assertThat(end)
                .as(file + " should contain a module governance end marker")
                .isGreaterThan(start);

        String ledger = content.substring(start + startMarker.length(), end);
        var matcher = Pattern.compile("\\|\\s*`(actiongraph-[^`]+)`\\s*\\|").matcher(ledger);
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

    private java.util.List<Path> springBootStarterDirectories(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("actiongraph-"))
                    .filter(path -> path.getFileName().toString().endsWith("-spring-boot-starter"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private Set<String> annotatedAutoConfigurationClasses(Path starter) throws IOException {
        return javaSourceFiles(starter).stream()
                .filter(path -> contains(path, "@AutoConfiguration"))
                .map(path -> toClassName(starter.resolve("src/main/java"), path))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> configurationPropertiesClasses(Path starter) throws IOException {
        return javaSourceFiles(starter).stream()
                .filter(path -> contains(path, "@ConfigurationProperties"))
                .map(path -> toClassName(starter.resolve("src/main/java"), path))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> enabledConfigurationPropertiesClasses(Path starter) throws IOException {
        Pattern pattern = Pattern.compile("@EnableConfigurationProperties\\((.*?)\\)", Pattern.DOTALL);
        Set<String> classes = new LinkedHashSet<>();
        for (Path sourceFile : javaSourceFiles(starter)) {
            var matcher = pattern.matcher(Files.readString(sourceFile, StandardCharsets.UTF_8));
            while (matcher.find()) {
                var classMatcher = Pattern.compile("([A-Za-z0-9_$.]+)\\.class").matcher(matcher.group(1));
                while (classMatcher.find()) {
                    classes.add(simpleClassName(classMatcher.group(1)));
                }
            }
        }
        return classes;
    }

    private java.util.List<Path> javaSourceFiles(Path starter) throws IOException {
        Path sourceRoot = starter.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            return java.util.List.of();
        }
        try (var paths = Files.walk(sourceRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private Set<String> autoConfigurationImports(Path starter) throws IOException {
        Path importsFile = starter.resolve(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        );
        if (!Files.exists(importsFile)) {
            return Set.of();
        }
        java.util.List<String> lines = Files.readAllLines(importsFile, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.toList());
        assertThat(lines)
                .as(importsFile + " should not contain duplicate auto-configuration entries")
                .doesNotHaveDuplicates();
        return new LinkedHashSet<>(lines);
    }

    private boolean contains(Path file, String text) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(text);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + file, ex);
        }
    }

    private String toClassName(Path sourceRoot, Path sourceFile) {
        String relative = sourceRoot.relativize(sourceFile).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace(java.io.File.separatorChar, '.');
    }

    private String simpleClassName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
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
