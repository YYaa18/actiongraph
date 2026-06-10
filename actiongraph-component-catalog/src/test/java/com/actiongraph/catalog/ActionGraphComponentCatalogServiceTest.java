package com.actiongraph.catalog;

import org.junit.jupiter.api.Test;

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
                .satisfies(component -> assertThat(component.requires())
                        .containsExactly("actiongraph-runtime-api", "actiongraph-control-plane-api"));
        assertThat(service.component("actiongraph-component-catalog-spring-boot-starter"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.requires())
                        .containsExactly("actiongraph-component-catalog", "actiongraph-control-plane-api"));
        assertThat(service.component("actiongraph-control-plane-api"))
                .isPresent()
                .get()
                .satisfies(component -> assertThat(component.capabilities())
                        .contains("java8-runtime-client", "shared-secret-token-verification"));
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
                .satisfies(profile -> assertThat(profile.modules())
                        .containsExactly("actiongraph-control-plane-api"));
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
}
