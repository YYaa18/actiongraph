import java.io.DataInputStream
import java.io.FileInputStream

plugins {
    java
}

group = "com.actiongraph"
version = "0.1.0"

val platformModuleName = "actiongraph-bom"

val java8CompatibleModules = setOf(
    "actiongraph-control-plane-api"
)

val libraryModuleDescriptions = mapOf(
    "actiongraph-core" to "Core GOAP agent runtime with actions, planning, execution, policy, trace, goal interpretation, runtime entry, and structured memory APIs.",
    "actiongraph-annotations" to "Optional pure Java annotation adapter for ActionGraph action registration.",
    "actiongraph-control-plane-api" to "Java 8 compatible component catalog, control-plane contracts, properties-based aggregate configuration, safe GET retries, lightweight aggregate and split HTTP clients, and shared-secret token verification.",
    "actiongraph-human-review" to "Optional repository-backed human review tasks, callbacks, approval chains, and task query APIs for ActionGraph.",
    "actiongraph-llm-deepseek" to "Provider-neutral LLM goal interpretation support and DeepSeek-compatible client provider for ActionGraph.",
    "actiongraph-persistence-jdbc" to "JDBC persistence repositories for ActionGraph trace, suspended runs, memory, human review, and read models.",
    "actiongraph-spring-boot-starter" to "Spring Boot auto-configuration for annotation-driven ActionGraph action registration, repositories, governance, memory, human review, and optional HTTP endpoints.",
    "actiongraph-governance" to "Optional governance policies for masking, amount limits, rule-based permissions, review attributes, and approval routing.",
    "actiongraph-console" to "Reusable ActionGraph console query, JDBC read model, and audit export services."
)

val publishableModuleDescriptions = mapOf(
    platformModuleName to "Bill of materials for aligning ActionGraph module versions."
) + libraryModuleDescriptions

val java8MavenConsumerDir = layout.projectDirectory.dir("docs/examples/java8-maven-consumer")
val java8MavenConsumerBuildDir = layout.buildDirectory.dir("java8-maven-consumer")

fun classFileMajorVersion(file: File): Int {
    DataInputStream(FileInputStream(file)).use { input ->
        val magic = input.readInt()
        if (magic != 0xCAFEBABE.toInt()) {
            throw GradleException("Not a class file: ${file.path}")
        }
        input.readUnsignedShort()
        return input.readUnsignedShort()
    }
}

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

        tasks.named<JavaCompile>("compileJava") {
            if (project.name in java8CompatibleModules) {
                options.release.set(8)
            }
        }

        if (name in java8CompatibleModules) {
            val verifyJava8Compatibility = tasks.register("verifyJava8Compatibility") {
                group = "verification"
                description = "Verifies Java 8 compatible artifacts keep Java 8 bytecode and no runtime dependencies."
                dependsOn(tasks.named("classes"))

                doLast {
                    val runtimeArtifacts = configurations.named("runtimeClasspath").get()
                            .resolvedConfiguration
                            .resolvedArtifacts
                    if (runtimeArtifacts.isNotEmpty()) {
                        val dependencies = runtimeArtifacts.joinToString(", ") {
                            "${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}"
                        }
                        throw GradleException("${project.name} must not have runtime dependencies: $dependencies")
                    }

                    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
                    if (classesDir.exists()) {
                        classesDir.walkTopDown()
                                .filter { it.isFile && it.extension == "class" }
                                .forEach { classFile ->
                                    val majorVersion = classFileMajorVersion(classFile)
                                    if (majorVersion > 52) {
                                        throw GradleException(
                                                "${project.name} produced non-Java-8 bytecode: " +
                                                        "${classFile.relativeTo(classesDir).path} has major version $majorVersion"
                                        )
                                    }
                                }
                    }
                }
            }

            tasks.named("check") {
                dependsOn(verifyJava8Compatibility)
            }
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

val verifyJava8MavenConsumer = tasks.register<Exec>("verifyJava8MavenConsumer") {
    group = "verification"
    description = "Verifies a Java 8 Maven consumer can import the BOM and Java 8 ActionGraph client artifacts."
    dependsOn(
            ":actiongraph-bom:publishToMavenLocal",
            ":actiongraph-control-plane-api:publishToMavenLocal"
    )
    workingDir = java8MavenConsumerDir.asFile
    inputs.dir(java8MavenConsumerDir)
    outputs.dir(java8MavenConsumerBuildDir)
    outputs.upToDateWhen { false }
    commandLine(
            "mvn",
            "-q",
            "-Djava8.consumer.build.directory=${java8MavenConsumerBuildDir.get().asFile.absolutePath}",
            "clean",
            "compile"
    )
}

tasks.named("check") {
    dependsOn(verifyJava8MavenConsumer)
}
