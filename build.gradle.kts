plugins {
    java
}

group = "com.actiongraph"
version = "0.1.0"

val publishableModuleDescriptions = mapOf(
    "actiongraph-core" to "Core GOAP agent runtime with actions, planning, execution, policy, trace, and interpretation APIs.",
    "actiongraph-llm-deepseek" to "DeepSeek-compatible LLM goal interpretation support for ActionGraph.",
    "actiongraph-persistence-jdbc" to "JDBC persistence repositories for ActionGraph trace events and suspended runs.",
    "actiongraph-spring-boot-starter" to "Spring Boot auto-configuration for annotation-driven ActionGraph action registration and human-review callbacks."
)

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version
    description = publishableModuleDescriptions[name] ?: "ActionGraph sample application."

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

    if (name in publishableModuleDescriptions.keys) {
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
