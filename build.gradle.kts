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
    "actiongraph-human-review" to "Optional repository-backed human review tasks, callbacks, and approval chains for ActionGraph.",
    "actiongraph-llm" to "Provider-neutral LLM goal interpretation support for ActionGraph.",
    "actiongraph-llm-deepseek" to "DeepSeek-compatible LLM client provider for ActionGraph.",
    "actiongraph-persistence-jdbc" to "JDBC persistence repositories for ActionGraph trace, suspended runs, review tasks, and memory.",
    "actiongraph-jdbc-spring-boot-starter" to "Optional Spring Boot auto-configuration for ActionGraph JDBC repositories.",
    "actiongraph-spring-boot-starter" to "Spring Boot auto-configuration for annotation-driven ActionGraph action registration.",
    "actiongraph-governance" to "Optional governance policies for masking, amount limits, approval routing, and rule-based permissions.",
    "actiongraph-governance-spring-boot-starter" to "Optional Spring Boot governance policies for masking, approval routing, and amount limits.",
    "actiongraph-console-core" to "Reusable ActionGraph console query service and response model.",
    "actiongraph-console-jdbc" to "JDBC adapter for the ActionGraph console query port.",
    "actiongraph-human-review-spring-boot-starter" to "Optional Spring Boot repository-backed human-review policy and callback endpoint support for ActionGraph.",
    "actiongraph-console-spring-boot-starter" to "Optional Spring Boot read-only console UI and query endpoints for ActionGraph JDBC trace runs."
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
