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
                                    "observation-sink-autoconfiguration", "micrometer-observability",
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
                                "observability-spi",
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
    void publishedLibraryModulesRespectArchitectureDependencyGraph() throws IOException {
        Path root = repositoryRoot();
        java.util.Map<String, Set<String>> allowedDependencies = java.util.Map.of(
                "actiongraph-core", Set.of(),
                "actiongraph-control-plane-api", Set.of(),
                "actiongraph-human-review", Set.of("actiongraph-core"),
                "actiongraph-governance", Set.of("actiongraph-core", "actiongraph-human-review"),
                "actiongraph-llm-deepseek", Set.of("actiongraph-core"),
                "actiongraph-persistence-jdbc", Set.of("actiongraph-core", "actiongraph-human-review"),
                "actiongraph-console", Set.of("actiongraph-core", "actiongraph-persistence-jdbc"),
                "actiongraph-spring-boot-starter", Set.of(
                        "actiongraph-core",
                        "actiongraph-control-plane-api",
                        "actiongraph-console",
                        "actiongraph-governance",
                        "actiongraph-human-review",
                        "actiongraph-persistence-jdbc")
        );

        assertThat(allowedDependencies.keySet())
                .as("architecture dependency graph should cover every published library module")
                .containsExactlyInAnyOrderElementsOf(parseLibraryModules(root.resolve("build.gradle.kts")));
        for (var entry : allowedDependencies.entrySet()) {
            Set<String> gradleDependencies = parseModules(
                    root.resolve(entry.getKey()).resolve("build.gradle.kts"),
                    "(?:api|implementation|compileOnly|runtimeOnly)\\(project\\(\":([^\"]+)\"\\)\\)"
            );
            assertThat(gradleDependencies)
                    .as(entry.getKey() + " must only depend on approved lower-layer modules")
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
    }

    @Test
    void publishedMainSourcesRespectArchitectureImportBoundaries() throws IOException {
        Path root = repositoryRoot();
        Set<String> libraryModules = parseLibraryModules(root.resolve("build.gradle.kts"));
        Set<String> nonSpringModules = libraryModules.stream()
                .filter(module -> !module.equals("actiongraph-spring-boot-starter"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> coreForbiddenImports = Set.of(
                "org.springframework.",
                "io.micrometer.",
                "java.sql.",
                "javax.sql.",
                "com.fasterxml.",
                "okhttp3.",
                "com.actiongraph.samples."
        );

        for (String module : libraryModules) {
            Path modulePath = root.resolve(module);
            for (Path sourceFile : javaSourceFiles(modulePath)) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                assertThat(source)
                        .as(module + " library main sources must not depend on sample code: "
                                + root.relativize(sourceFile))
                        .doesNotContain("import com.actiongraph.samples.");
                if (nonSpringModules.contains(module)) {
                    assertThat(source)
                            .as(module + " is not the Spring integration layer: " + root.relativize(sourceFile))
                            .doesNotContain("import org.springframework.");
                }
                if (module.equals("actiongraph-core")) {
                    for (String forbiddenImport : coreForbiddenImports) {
                        assertThat(source)
                                .as("core must remain provider, persistence, sample, and Spring agnostic: "
                                        + root.relativize(sourceFile))
                                .doesNotContain("import " + forbiddenImport);
                    }
                }
            }
        }
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
    void repositoryGovernanceFilesArePresentAndLinked() throws IOException {
        Path root = repositoryRoot();
        String license = Files.readString(root.resolve("LICENSE"), StandardCharsets.UTF_8);
        String security = Files.readString(root.resolve("SECURITY.md"), StandardCharsets.UTF_8);
        String contributing = Files.readString(root.resolve("CONTRIBUTING.md"), StandardCharsets.UTF_8);
        String changelog = Files.readString(root.resolve("CHANGELOG.md"), StandardCharsets.UTF_8);
        String stableContract = Files.readString(root.resolve("STABLE_CONTRACT.md"), StandardCharsets.UTF_8);
        String readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8);
        String chineseReadme = Files.readString(root.resolve("README.zh-CN.md"), StandardCharsets.UTF_8);

        assertThat(license)
                .contains("Apache License")
                .contains("Version 2.0")
                .contains("Copyright 2026 ActionGraph contributors");
        assertThat(security)
                .contains("Do not open a public issue")
                .contains("Supported Versions")
                .contains("GitHub private vulnerability reporting")
                .contains("Do not commit API keys, production endpoints, source credentials, raw PII");
        assertThat(contributing)
                .contains("./gradlew build --rerun-tasks")
                .contains("STABLE_CONTRACT.md")
                .contains("CHANGELOG.md")
                .contains("Do not add a new Gradle module without updating");
        assertThat(changelog)
                .contains("## Unreleased")
                .contains("## 0.1.0 - 2026-06-11")
                .contains("Apache 2.0 license")
                .contains("Batch goal interpretation SPI");
        assertThat(stableContract)
                .contains("semantic versioning")
                .contains("Public API Surface")
                .contains("Deprecation Policy")
                .contains("actiongraph-control-plane-api")
                .contains("Java 8 compatible client artifact")
                .contains("Sample packages under `com.actiongraph.samples` are executable documentation");
        assertThat(readme)
                .contains("[License](LICENSE)")
                .contains("[Security](SECURITY.md)")
                .contains("[Contributing](CONTRIBUTING.md)")
                .contains("[Stable Contract](STABLE_CONTRACT.md)")
                .contains("[Changelog](CHANGELOG.md)");
        assertThat(chineseReadme)
                .contains("[安全政策](SECURITY.md)")
                .contains("[贡献指南](CONTRIBUTING.md)")
                .contains("[稳定契约](STABLE_CONTRACT.md)")
                .contains("[变更日志](CHANGELOG.md)");
    }

    @Test
    void coreLoggingUsesSlf4jApiWithoutBindingImplementation() throws IOException {
        Path root = repositoryRoot();
        String coreBuild = Files.readString(root.resolve("actiongraph-core/build.gradle.kts"),
                StandardCharsets.UTF_8);
        String bomBuild = Files.readString(root.resolve("actiongraph-bom/build.gradle.kts"),
                StandardCharsets.UTF_8);
        String planner = Files.readString(root.resolve(
                "actiongraph-core/src/main/java/com/actiongraph/planning/GoapPlanner.java"),
                StandardCharsets.UTF_8);
        String executor = Files.readString(root.resolve(
                "actiongraph-core/src/main/java/com/actiongraph/runtime/GoapExecutor.java"),
                StandardCharsets.UTF_8);
        String suspendedRuns = Files.readString(root.resolve(
                "actiongraph-core/src/main/java/com/actiongraph/runtime/InMemorySuspendedRunRepository.java"),
                StandardCharsets.UTF_8);
        String coreSources = planner + "\n" + executor + "\n" + suspendedRuns;

        assertThat(coreBuild)
                .contains("implementation(\"org.slf4j:slf4j-api:")
                .doesNotContain("slf4j-simple", "logback-classic", "log4j-core");
        assertThat(bomBuild)
                .contains("api(\"org.slf4j:slf4j-api:")
                .doesNotContain("slf4j-simple", "logback-classic", "log4j-core");
        assertThat(coreSources)
                .contains("org.slf4j.Logger")
                .contains("LoggerFactory.getLogger(GoapPlanner.class)")
                .contains("LoggerFactory.getLogger(GoapExecutor.class)")
                .contains("LoggerFactory.getLogger(InMemorySuspendedRunRepository.class)")
                .contains("Planning goal")
                .contains("Plan generated")
                .contains("Policy evaluated")
                .contains("Runtime guard failed")
                .contains("Human review requested")
                .contains("Compensation started")
                .contains("Saving suspended run")
                .contains("Suspended run claim attempted")
                .contains("Run finished");
        for (Path sourceFile : javaSourceFiles(root.resolve("actiongraph-core"))) {
            String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
            assertThat(source)
                    .as(sourceFile + " should not use console output for framework diagnostics")
                    .doesNotContain("System.out", "System.err");
        }
    }

    @Test
    void corePublicContractsDocumentThreadSafetyNullAndLifecycleSemantics() throws IOException {
        Path root = repositoryRoot();
        java.util.List<String> contractFiles = java.util.List.of(
                "actiongraph-core/src/main/java/com/actiongraph/action/Action.java",
                "actiongraph-core/src/main/java/com/actiongraph/action/ActionRegistry.java",
                "actiongraph-core/src/main/java/com/actiongraph/action/ExecutionContext.java",
                "actiongraph-core/src/main/java/com/actiongraph/runtime/Blackboard.java",
                "actiongraph-core/src/main/java/com/actiongraph/runtime/SuspendedRunRepository.java",
                "actiongraph-core/src/main/java/com/actiongraph/trace/TraceRepository.java",
                "actiongraph-core/src/main/java/com/actiongraph/policy/ExecutionPolicyGuard.java",
                "actiongraph-core/src/main/java/com/actiongraph/policy/HumanReviewPolicy.java",
                "actiongraph-core/src/main/java/com/actiongraph/policy/HumanReviewRequest.java",
                "actiongraph-core/src/main/java/com/actiongraph/policy/DataMaskingPolicy.java",
                "actiongraph-core/src/main/java/com/actiongraph/trace/TraceEvent.java"
        );

        for (String contractFile : contractFiles) {
            assertThat(readSource(root, contractFile))
                    .as(contractFile + " should have public contract Javadocs")
                    .contains("/**");
        }

        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/action/Action.java"))
                .contains("storing per-run mutable state")
                .contains("Null contract")
                .contains("best-effort compensation")
                .contains("intentionally invisible to the planner");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/runtime/Blackboard.java"))
                .contains("Mutable per-run working memory")
                .contains("Do not share")
                .contains("required to be thread-safe")
                .contains("return empty collections");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/runtime/SuspendedRunRepository.java"))
                .contains("claimForResume")
                .contains("atomic claim")
                .contains("concurrent approval callbacks cannot execute the same pending action twice");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/trace/TraceRepository.java"))
                .contains("appendAll")
                .contains("preserve the order")
                .contains("commit a batch atomically");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/policy/HumanReviewPolicy.java"))
                .contains("must not execute the business action itself")
                .contains("Thread-safety");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/policy/DataMaskingPolicy.java"))
                .contains("return non-null values")
                .contains("masking before hash calculation");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/policy/ExecutionPolicyGuard.java"))
                .contains("execute the action")
                .contains("return a non-null");
        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/trace/TraceEvent.java"))
                .contains("tamper-evident chain")
                .contains("should already be masked");
    }

    @Test
    void publishedSourcePackagesDeclareJSpecifyNullSafetyContracts() throws IOException {
        Path root = repositoryRoot();
        String build = readSource(root, "build.gradle.kts");
        String bom = readSource(root, "actiongraph-bom/build.gradle.kts");

        assertThat(build)
                .contains("val jspecifyVersion = \"1.0.0\"")
                .contains("compileOnlyApi")
                .contains("org.jspecify:jspecify:$jspecifyVersion");
        assertThat(bom)
                .contains("org.jspecify:jspecify:1.0.0");

        for (Path packageDirectory : publishedSourcePackageDirectories(root)) {
            Path packageInfo = packageDirectory.resolve("package-info.java");
            assertThat(packageInfo)
                    .as("Published source package should declare JSpecify null defaults: "
                            + root.relativize(packageDirectory))
                    .exists();
            assertThat(Files.readString(packageInfo, StandardCharsets.UTF_8))
                    .as(packageInfo + " should opt into non-null-by-default")
                    .contains("@NullMarked")
                    .contains("org.jspecify.annotations.NullMarked");
        }

        assertThat(readSource(root, "actiongraph-core/src/main/java/com/actiongraph/policy/DataMaskingPolicy.java"))
                .contains("String maskText(@Nullable String text)")
                .contains("Map<String, String> maskData(@Nullable Map<String, String> data)");
        assertThat(readSource(root,
                "actiongraph-core/src/main/java/com/actiongraph/runtime/api/ActionGraphRuntimeOperations.java"))
                .contains("RuntimeInterpretationResponse interpret(String input, @Nullable Map<String, String>")
                .contains("RuntimeRunResponse resume(String runId, @Nullable Map<String, String>");
        assertThat(readSource(root,
                "actiongraph-control-plane-api/src/main/java/com/actiongraph/controlplane/api/ActionGraphRuntimeHttpClient.java"))
                .contains("interpret(String input, @Nullable Map<String, String> knownParameters)")
                .contains("resume(String runId, @Nullable Map<String, String> requestHeaders)");
        assertThat(readSource(root, "actiongraph-console/src/main/java/com/actiongraph/console/ConsoleRunQuery.java"))
                .contains("@Nullable String status")
                .contains("@Nullable Boolean auditComplete");
        assertThat(readSource(root,
                "actiongraph-persistence-jdbc/src/main/java/com/actiongraph/persistence/jdbc/TraceRunQuery.java"))
                .contains("@Nullable String status")
                .contains("@Nullable Boolean auditComplete");
    }

    @Test
    void strategyDocumentsKeepF1AsRealWorldGateNotSampleCompletion() throws IOException {
        Path root = repositoryRoot();
        String strategy = Files.readString(root.resolve("docs/finance-strategy.md"), StandardCharsets.UTF_8);
        String claimsNotes = Files.readString(root.resolve("docs/f1-claims-precheck-notes.md"), StandardCharsets.UTF_8);
        String pilotPack = Files.readString(root.resolve("docs/f1-pilot-validation-pack.md"), StandardCharsets.UTF_8);
        String readinessStatus = Files.readString(root.resolve("docs/f1-readiness-status.md"),
                StandardCharsets.UTF_8);
        String pilotIssueTemplate = Files.readString(root.resolve(".github/ISSUE_TEMPLATE/f1-pilot-validation.yml"),
                StandardCharsets.UTF_8);
        String chineseReadme = Files.readString(root.resolve("README.zh-CN.md"), StandardCharsets.UTF_8);
        String pitch = Files.readString(root.resolve("docs/actiongraph-pitch.html"), StandardCharsets.UTF_8);
        String moduleGovernance = Files.readString(root.resolve("docs/frameworkization/module-governance.md"),
                StandardCharsets.UTF_8);
        String combined = strategy + "\n" + claimsNotes + "\n" + pilotPack + "\n" + readinessStatus + "\n"
                + pilotIssueTemplate + "\n" + chineseReadme + "\n" + pitch + "\n" + moduleGovernance;

        assertThat(strategy)
                .contains("真实/准真实环境落地")
                .contains("业务方愿意续用并出具案例")
                .contains("理赔预审样板域从现在起冻结新增演示能力");
        assertThat(claimsNotes)
                .contains("样板域从现在起冻结新增功能")
                .contains("真实/准真实环境对接")
                .contains("新的报表、控制台页面、模拟输入和样例流程必须等真实试点反馈后再定")
                .contains("docs/f1-pilot-validation-pack.md");
        assertThat(pilotPack)
                .contains("Entry Criteria")
                .contains("Field Mapping Worksheet")
                .contains("Approval Integration Worksheet")
                .contains("Go / No-Go")
                .contains("Without the exit artifact, F1 remains open")
                .contains("F1 is not achieved when the only evidence is a local CSV, JSONL replay")
                .contains("docs/examples/f1-pilot-evidence-template/");
        Path evidenceTemplate = root.resolve("docs/examples/f1-pilot-evidence-template");
        for (String template : java.util.List.of(
                "00-summary.md",
                "01-data-source.md",
                "02-field-mapping.csv",
                "03-approval-integration.md",
                "04-run-metrics.csv",
                "05-trace-audit-export.jsonl",
                "06-compensation-drill.md",
                "07-business-signoff.md")) {
            assertThat(evidenceTemplate.resolve(template))
                    .as("F1 pilot evidence template should include " + template)
                    .exists();
        }
        assertThat(Files.readString(root.resolve("docs/examples/f1-pilot-evidence-template/00-summary.md"),
                StandardCharsets.UTF_8))
                .contains("Go / No-Go")
                .contains("Real or near-real data source used")
                .contains("No raw PII, tokens, or source credentials committed");
        assertThat(Files.readString(root.resolve("docs/examples/f1-pilot-evidence-template/03-approval-integration.md"),
                StandardCharsets.UTF_8))
                .contains("Duplicate delivery")
                .contains("Wrong stage")
                .contains("Bad token");
        assertThat(Files.readString(root.resolve("docs/examples/f1-pilot-evidence-template/07-business-signoff.md"),
                StandardCharsets.UTF_8))
                .contains("Business owner")
                .contains("Business owner wants to continue");
        assertThat(readinessStatus)
                .contains("| Module consolidation | Done |")
                .contains("| Real or near-real data source | Not done |")
                .contains("| Real or shadow approval path | Not done |")
                .contains("| Business owner sign-off | Not done |")
                .contains("| F1 achieved | Not done |")
                .contains("pilot-ready, F1 open")
                .contains("verifyJava8MavenConsumer")
                .contains(".github/ISSUE_TEMPLATE/f1-pilot-validation.yml");
        assertThat(pilotIssueTemplate)
                .contains("name: F1 pilot validation")
                .contains("real or near-real pilot validation")
                .contains("Local CSV runs, JSONL replay, generated reports, static console output")
                .contains("Business sign-off")
                .contains("Without this, F1 remains open")
                .contains("Duplicate delivery is idempotent")
                .contains("Bad token is rejected before state mutation");
        assertThat(chineseReadme)
                .contains("F1 是否成立仍以真实/准真实环境落地、业务方续用意愿和案例证据为门槛")
                .contains("样板域已冻结新增演示能力")
                .contains("docs/f1-readiness-status.md")
                .contains("docs/f1-pilot-validation-pack.md")
                .contains("docs/examples/f1-pilot-evidence-template");
        assertThat(pitch)
                .contains("F1 是否成立仍取决于真实/准真实环境落地、业务方续用意愿和案例证据");
        assertThat(moduleGovernance)
                .contains("current 10-module surface")
                .doesNotContain("current 11-module surface");
        assertThat(combined)
                .doesNotContain("F1 已进入场景打穿")
                .doesNotContain("F1 场景打穿已有")
                .doesNotContain("当前 11")
                .doesNotContain("29</b><span>modules")
                .doesNotContain("328</b><span>tests passing");
    }

    @Test
    void runtimeInvocationDocsKeepFrameworkOwnedInterfacesAndApplicationOwnedEntryPoints() throws IOException {
        Path root = repositoryRoot();
        String invocationSpi = Files.readString(root.resolve("docs/frameworkization/runtime-invocation-spi.md"),
                StandardCharsets.UTF_8);
        String runtimeApi = Files.readString(root.resolve("docs/frameworkization/runtime-api.md"),
                StandardCharsets.UTF_8);
        String readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8);
        String chineseReadme = Files.readString(root.resolve("README.zh-CN.md"), StandardCharsets.UTF_8);
        String combined = invocationSpi + "\n" + runtimeApi + "\n" + readme + "\n" + chineseReadme;

        assertThat(invocationSpi)
                .contains("ActionGraph is a framework ecosystem")
                .contains("ActionGraphRuntimeOperations")
                .contains("BatchGoalInterpreter")
                .contains("structured source records should bypass LLM interpretation")
                .contains("ActionGraphRuntimeGateway")
                .contains("ActionGraphRuntimeHttpClient")
                .contains("LlmClient")
                .contains("Do not invoke Gradle or sample CLI commands from production");
        assertThat(runtimeApi)
                .contains("ActionGraphRuntimeOperations runtime = new ActionGraphRuntimeApiService")
                .contains("Production systems should not invoke Gradle or sample CLI commands")
                .contains("BatchGoalInterpreter")
                .contains("LlmClient");
        assertThat(readme)
                .contains("Runtime operations SPI")
                .contains("Batch goal interpretation SPI")
                .contains("docs/frameworkization/runtime-invocation-spi.md");
        assertThat(chineseReadme)
                .contains("目标解释与 Runtime SPI")
                .contains("批量目标解释 SPI")
                .contains("生产系统不应该通过 Gradle 或样例命令行")
                .contains("docs/frameworkization/runtime-invocation-spi.md");
        assertThat(combined)
                .doesNotContain("production should run Gradle")
                .doesNotContain("生产通过 Gradle");
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

    private Set<Path> publishedSourcePackageDirectories(Path root) throws IOException {
        Set<Path> packages = new LinkedHashSet<>();
        for (String module : parseLibraryModules(root.resolve("build.gradle.kts"))) {
            Path sourceRoot = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                        .map(Path::getParent)
                        .sorted()
                        .forEach(packages::add);
            }
        }
        return packages;
    }

    private Set<String> parseLibraryModules(Path buildFile) throws IOException {
        String content = Files.readString(buildFile, StandardCharsets.UTF_8);
        var matcher = Pattern.compile(
                "val\\s+libraryModuleDescriptions\\s*=\\s*mapOf\\((.*?)\\)\\s*\\n\\s*val\\s+publishableModuleDescriptions",
                Pattern.DOTALL
        ).matcher(content);
        assertThat(matcher.find())
                .as("root build must declare libraryModuleDescriptions")
                .isTrue();

        Set<String> modules = new LinkedHashSet<>();
        var moduleMatcher = Pattern.compile("\"(actiongraph-[^\"]+)\"\\s+to").matcher(matcher.group(1));
        while (moduleMatcher.find()) {
            modules.add(moduleMatcher.group(1));
        }
        return modules;
    }

    private String readSource(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
