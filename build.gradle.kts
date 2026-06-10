plugins {
    java
}

group = "com.actiongraph"
version = "0.1.0"

val platformModuleName = "actiongraph-bom"

val libraryModuleDescriptions = mapOf(
    "actiongraph-core" to "Core GOAP agent runtime with actions, planning, execution, policy, and trace APIs.",
    "actiongraph-annotations" to "Optional pure Java annotation adapter for ActionGraph action registration.",
    "actiongraph-memory" to "Optional structured memory context support for ActionGraph.",
    "actiongraph-memory-spring-boot-starter" to "Optional Spring Boot auto-configuration for ActionGraph structured memory.",
    "actiongraph-interpretation" to "Optional goal interpretation contracts and GoalCatalog support for ActionGraph.",
    "actiongraph-runtime-api" to "Reusable ActionGraph goal interpretation, start, and resume API service.",
    "actiongraph-component-catalog" to "Reusable machine-readable ActionGraph component catalog and composition profile service.",
    "actiongraph-control-plane-api" to "Reusable control-plane response contracts for endpoint adapters.",
    "actiongraph-control-plane-auth" to "Reusable control-plane shared-secret token verification support.",
    "actiongraph-human-review" to "Optional repository-backed human review tasks, callbacks, and approval chains for ActionGraph.",
    "actiongraph-human-review-api" to "Reusable ActionGraph human-review task query and decision API service.",
    "actiongraph-llm" to "Provider-neutral LLM goal interpretation support for ActionGraph.",
    "actiongraph-llm-deepseek" to "DeepSeek-compatible LLM client provider for ActionGraph.",
    "actiongraph-persistence-jdbc" to "Core JDBC persistence repositories for ActionGraph trace and suspended runs.",
    "actiongraph-memory-jdbc" to "Optional JDBC persistence repository for ActionGraph structured memory.",
    "actiongraph-human-review-jdbc" to "Optional JDBC persistence repository for ActionGraph human-review tasks.",
    "actiongraph-jdbc-spring-boot-starter" to "Optional Spring Boot auto-configuration for ActionGraph core JDBC repositories.",
    "actiongraph-memory-jdbc-spring-boot-starter" to "Optional Spring Boot auto-configuration for ActionGraph JDBC memory repository.",
    "actiongraph-human-review-jdbc-spring-boot-starter" to "Optional Spring Boot auto-configuration for ActionGraph JDBC human-review repository.",
    "actiongraph-runtime-api-spring-boot-starter" to "Optional Spring MVC runtime start and resume endpoints for ActionGraph.",
    "actiongraph-component-catalog-spring-boot-starter" to "Optional Spring MVC read-only endpoints for the ActionGraph component catalog.",
    "actiongraph-human-review-api-spring-boot-starter" to "Optional Spring MVC human-review task API endpoints for ActionGraph.",
    "actiongraph-human-review-callback-spring-boot-starter" to "Optional Spring MVC callback endpoint for ActionGraph human-review decisions.",
    "actiongraph-spring-boot-starter" to "Spring Boot auto-configuration for annotation-driven ActionGraph action registration.",
    "actiongraph-governance" to "Optional governance policies for masking, amount limits, and rule-based permissions.",
    "actiongraph-governance-human-review" to "Optional governance policies that enrich human-review approval chains and review attributes.",
    "actiongraph-governance-spring-boot-starter" to "Optional Spring Boot governance policies for masking, amount limits, and rule-based permissions.",
    "actiongraph-governance-human-review-spring-boot-starter" to "Optional Spring Boot governance policies for human-review routing and review attributes.",
    "actiongraph-console-core" to "Reusable ActionGraph console query service and response model.",
    "actiongraph-console-jdbc" to "JDBC adapter for the ActionGraph console query port.",
    "actiongraph-console-export" to "Reusable ActionGraph console audit export service for CSV and JSONL output.",
    "actiongraph-console-spring-boot-autoconfigure" to "Optional Spring Boot service auto-configuration for ActionGraph console components.",
    "actiongraph-console-api-spring-boot-starter" to "Optional Spring MVC JSON API endpoints for the ActionGraph console.",
    "actiongraph-console-ui-spring-boot-starter" to "Optional Spring MVC HTML page for the ActionGraph console.",
    "actiongraph-console-export-spring-boot-starter" to "Optional Spring MVC audit export endpoints for the ActionGraph console.",
    "actiongraph-console-jdbc-spring-boot-starter" to "Optional Spring Boot auto-configuration for ActionGraph JDBC console read model.",
    "actiongraph-human-review-spring-boot-starter" to "Optional Spring Boot repository-backed human-review policy support for ActionGraph.",
    "actiongraph-console-spring-boot-starter" to "Compatibility aggregate starter for the ActionGraph console API and UI.",
    "actiongraph-control-plane-spring-boot-starter" to "Optional aggregate starter for ActionGraph runtime, component catalog, human-review, and console control-plane endpoints."
)

val publishableModuleDescriptions = mapOf(
    platformModuleName to "Bill of materials for aligning ActionGraph module versions."
) + libraryModuleDescriptions

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
    description = publishableModuleDescriptions[name] ?: "ActionGraph sample application."

    if (name == platformModuleName) {
        apply(plugin = "java-platform")
        apply(plugin = "maven-publish")

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["javaPlatform"])
                    artifactId = project.name

                    pom {
                        name.set(project.name)
                        description.set(project.description)
                    }
                }
            }

            repositories {
                maven {
                    name = "actionGraph"
                    val publishUrl = findProperty("actionGraphPublishUrl")?.toString()
                    url = if (publishUrl.isNullOrBlank()) {
                        uri(layout.buildDirectory.dir("repository"))
                    } else {
                        uri(publishUrl)
                    }

                    val publishUsername = findProperty("actionGraphPublishUsername")?.toString()
                            ?: System.getenv("ACTIONGRAPH_PUBLISH_USERNAME")
                    val publishPassword = findProperty("actionGraphPublishPassword")?.toString()
                            ?: System.getenv("ACTIONGRAPH_PUBLISH_PASSWORD")
                    if (!publishUsername.isNullOrBlank()) {
                        credentials {
                            username = publishUsername
                            password = publishPassword ?: ""
                        }
                    }
                }
            }
        }
    } else {
        apply(plugin = "java")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.10.3"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testImplementation"("org.assertj:assertj-core:3.26.3")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }

        tasks.withType<Javadoc> {
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }

        if (name in libraryModuleDescriptions.keys) {
            apply(plugin = "maven-publish")

            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }

            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        artifactId = project.name

                        pom {
                            name.set(project.name)
                            description.set(project.description)
                        }
                    }
                }

                repositories {
                    maven {
                        name = "actionGraph"
                        val publishUrl = findProperty("actionGraphPublishUrl")?.toString()
                        url = if (publishUrl.isNullOrBlank()) {
                            uri(layout.buildDirectory.dir("repository"))
                        } else {
                            uri(publishUrl)
                        }

                        val publishUsername = findProperty("actionGraphPublishUsername")?.toString()
                                ?: System.getenv("ACTIONGRAPH_PUBLISH_USERNAME")
                        val publishPassword = findProperty("actionGraphPublishPassword")?.toString()
                                ?: System.getenv("ACTIONGRAPH_PUBLISH_PASSWORD")
                        if (!publishUsername.isNullOrBlank()) {
                            credentials {
                                username = publishUsername
                                password = publishPassword ?: ""
                            }
                        }
                    }
                }
            }
        }
    }
}
