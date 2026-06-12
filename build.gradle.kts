import java.io.DataInputStream
import java.io.FileInputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.JavaExec

plugins {
    java
}

group = "com.actiongraph"
version = "0.2.0-SNAPSHOT"

val platformModuleName = "actiongraph-bom"
val jspecifyVersion = "1.0.0"
val japicmpVersion = "0.26.1"

val java8CompatibleModules = setOf(
    "actiongraph-control-plane-api"
)

val libraryModuleDescriptions = mapOf(
    "actiongraph-core" to "Core GOAP agent runtime with actions, planning, execution, policy, trace, observability, SLF4J API diagnostics, goal interpretation, runtime entry, and structured memory APIs.",
    "actiongraph-control-plane-api" to "Java 8 compatible component catalog, control-plane contracts, runtime gateway interface, properties-based aggregate configuration, safe GET retries, lightweight aggregate and split HTTP clients, and shared-secret token verification.",
    "actiongraph-human-review" to "Optional repository-backed human review tasks, callbacks, approval chains, and task query APIs for ActionGraph.",
    "actiongraph-llm-deepseek" to "Provider-neutral LLM goal interpretation support and DeepSeek-compatible client provider for ActionGraph.",
    "actiongraph-persistence-jdbc" to "JDBC persistence repositories for ActionGraph trace, suspended runs, memory, human review, and read models.",
    "actiongraph-spring-boot-starter" to "Spring Boot auto-configuration for annotation-driven ActionGraph action registration, observability, repositories, governance, memory, human review, and optional HTTP endpoints.",
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

    if (name in libraryModuleDescriptions.keys) {
        pluginManager.withPlugin("java-library") {
            dependencies.add("compileOnlyApi", "org.jspecify:jspecify:$jspecifyVersion")
        }
    }

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

val publicApiSnapshotFile = layout.projectDirectory.file("docs/api/public-api.snapshot")
val binaryCompatibilityReportDir = layout.buildDirectory.dir("reports/binary-compatibility")
val binaryCompatibilityBaselineVersion =
        providers.gradleProperty("actionGraphBaselineVersion")
                .orElse(providers.environmentVariable("ACTIONGRAPH_BASELINE_VERSION"))
val binaryCompatibilityBaselineRepositoryUrl =
        providers.gradleProperty("actionGraphBaselineRepositoryUrl")
                .orElse(providers.environmentVariable("ACTIONGRAPH_BASELINE_REPOSITORY_URL"))
val useMavenLocalBinaryCompatibilityBaseline =
        providers.gradleProperty("actionGraphUseMavenLocalBaseline")
                .map(String::toBoolean)
                .orElse(false)
val binaryCompatibilityExcludes = listOf(
        "@com.actiongraph.api.Experimental",
        "@com.actiongraph.api.Internal",
        "com.actiongraph.memory.*",
        "com.actiongraph.runtime.api.batch.*",
        "com.actiongraph.llm.*",
        "com.actiongraph.runtime.DefaultExecutionContext",
        "com.actiongraph.console.ConsolePageRenderer",
        "com.actiongraph.persistence.jdbc.PersistenceJsonCodec"
)

repositories {
    if (useMavenLocalBinaryCompatibilityBaseline.get()) {
        mavenLocal()
    }
    mavenCentral()
    binaryCompatibilityBaselineRepositoryUrl.orNull?.let { repositoryUrl ->
        maven {
            name = "actionGraphBinaryCompatibilityBaseline"
            url = uri(repositoryUrl)
        }
    }
}

val japicmpCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "JApiCmp CLI used by verifyBinaryCompatibility."
}

dependencies {
    japicmpCli("com.github.siom79.japicmp:japicmp:$japicmpVersion:jar-with-dependencies")
}

fun isAtLeastOneDotZero(version: String): Boolean {
    val baseVersion = version.substringBefore('-')
    val major = baseVersion.substringBefore('.').toIntOrNull() ?: return false
    return major >= 1
}

fun publicOrProtected(modifiers: Int): Boolean =
        Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)

fun stableModifiers(modifiers: Int, defaultMethod: Boolean = false): String {
    val parts = mutableListOf<String>()
    if (Modifier.isPublic(modifiers)) {
        parts += "public"
    } else if (Modifier.isProtected(modifiers)) {
        parts += "protected"
    }
    if (Modifier.isStatic(modifiers)) {
        parts += "static"
    }
    if (Modifier.isAbstract(modifiers)) {
        parts += "abstract"
    }
    if (Modifier.isFinal(modifiers)) {
        parts += "final"
    }
    if (Modifier.isSynchronized(modifiers)) {
        parts += "synchronized"
    }
    if (Modifier.isNative(modifiers)) {
        parts += "native"
    }
    if (Modifier.isStrict(modifiers)) {
        parts += "strict"
    }
    if (defaultMethod) {
        parts += "default"
    }
    return parts.joinToString(" ")
}

fun stableTypeName(type: Class<*>): String =
        when {
            type.isArray -> stableTypeName(type.componentType) + "[]"
            else -> type.name
        }

fun executableSignature(executable: Executable): String {
    val parameters = executable.parameterTypes.joinToString(",") { stableTypeName(it) }
    val exceptions = executable.exceptionTypes
            .map { stableTypeName(it) }
            .sorted()
            .joinToString(",")
    return if (exceptions.isBlank()) "($parameters)" else "($parameters) throws $exceptions"
}

fun classKind(type: Class<*>): String =
        when {
            type.isAnnotation -> "annotation"
            type.isEnum -> "enum"
            type.isRecord -> "record"
            type.isInterface -> "interface"
            else -> "class"
        }

fun renderField(field: Field): String =
        "  FIELD ${stableModifiers(field.modifiers)} ${stableTypeName(field.type)} ${field.name}"

fun renderConstructor(constructor: Constructor<*>): String =
        "  CONSTRUCTOR ${stableModifiers(constructor.modifiers)} ${constructor.name}${executableSignature(constructor)}"

fun renderMethod(method: Method): String =
        "  METHOD ${stableModifiers(method.modifiers, method.isDefault)} ${stableTypeName(method.returnType)} " +
                "${method.name}${executableSignature(method)}"

fun classNames(classesDir: File): List<String> =
        if (!classesDir.exists()) {
            emptyList()
        } else {
            classesDir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .map { it.relativeTo(classesDir).path }
                    .filterNot { it.endsWith("module-info.class") || it.endsWith("package-info.class") }
                    .map { it.removeSuffix(".class").replace(File.separatorChar, '.') }
                    .sorted()
                    .toList()
        }

fun renderPublicApiSnapshot(): String {
    val lines = mutableListOf<String>()
    lines += "# ActionGraph public API snapshot"
    lines += "# Regenerate intentionally with: ./gradlew verifyPublicApiSnapshot -PupdatePublicApiSnapshot=true"
    lines += "# This snapshot is generated from compiled public/protected class members."
    lines += ""

    libraryModuleDescriptions.keys.sorted().forEach { moduleName ->
        val moduleProject = project(":$moduleName")
        val sourceSets = moduleProject.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.named("main").get()
        val classesDirs = main.output.classesDirs.files.sortedBy { it.path }
        val classpath = (classesDirs + main.compileClasspath.files + main.runtimeClasspath.files)
                .distinct()
                .map { it.toURI().toURL() }
                .toTypedArray()
        URLClassLoader(classpath, ClassLoader.getPlatformClassLoader()).use { loader ->
            lines += "MODULE $moduleName"
            classesDirs.flatMap(::classNames)
                    .distinct()
                    .forEach { className ->
                        val type = Class.forName(className, false, loader)
                        if (publicOrProtected(type.modifiers) && !type.isSynthetic) {
                            lines += "CLASS ${stableModifiers(type.modifiers)} ${classKind(type)} ${type.name}"
                            type.declaredFields
                                    .filter { publicOrProtected(it.modifiers) && !it.isSynthetic }
                                    .sortedWith(compareBy<Field> { it.name }.thenBy { stableTypeName(it.type) })
                                    .mapTo(lines, ::renderField)
                            type.declaredConstructors
                                    .filter { publicOrProtected(it.modifiers) && !it.isSynthetic }
                                    .sortedBy { executableSignature(it) }
                                    .mapTo(lines, ::renderConstructor)
                            type.declaredMethods
                                    .filter { publicOrProtected(it.modifiers) && !it.isSynthetic && !it.isBridge }
                                    .sortedWith(compareBy<Method> { it.name }
                                            .thenBy { executableSignature(it) }
                                            .thenBy { stableTypeName(it.returnType) })
                                    .mapTo(lines, ::renderMethod)
                        }
                    }
            lines += ""
        }
    }
    return lines.joinToString(System.lineSeparator()) + System.lineSeparator()
}

val verifyPublicApiSnapshot = tasks.register("verifyPublicApiSnapshot") {
    group = "verification"
    description = "Verifies the published public/protected API surface matches the checked-in snapshot."
    dependsOn(libraryModuleDescriptions.keys.map { ":$it:classes" })
    inputs.files(libraryModuleDescriptions.keys.map { project(":$it").layout.buildDirectory.dir("classes/java/main") })
    outputs.upToDateWhen { false }

    doLast {
        val generated = renderPublicApiSnapshot()
        val snapshot = publicApiSnapshotFile.asFile
        if (findProperty("updatePublicApiSnapshot") == "true") {
            snapshot.parentFile.mkdirs()
            snapshot.writeText(generated)
            logger.lifecycle("Updated ${snapshot.relativeTo(projectDir)}")
            return@doLast
        }
        if (!snapshot.exists()) {
            throw GradleException(
                    "Missing public API snapshot. Run ./gradlew verifyPublicApiSnapshot " +
                            "-PupdatePublicApiSnapshot=true and review docs/api/public-api.snapshot."
            )
        }
        val expected = snapshot.readText()
        if (generated != expected) {
            val generatedFile = layout.buildDirectory.file("reports/public-api/public-api.snapshot.actual")
                    .get().asFile
            generatedFile.parentFile.mkdirs()
            generatedFile.writeText(generated)
            throw GradleException(
                    "Public API snapshot changed. Review ${generatedFile.relativeTo(projectDir)}. " +
                            "If intentional, run ./gradlew verifyPublicApiSnapshot " +
                            "-PupdatePublicApiSnapshot=true and document the compatibility impact."
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPublicApiSnapshot)
}

val binaryCompatibilityBaselineArtifacts by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Published ActionGraph artifacts used as the japicmp baseline."
}

if (binaryCompatibilityBaselineVersion.isPresent) {
    libraryModuleDescriptions.keys.sorted().forEach { moduleName ->
        dependencies.add(
                binaryCompatibilityBaselineArtifacts.name,
                "${project.group}:$moduleName:${binaryCompatibilityBaselineVersion.get()}"
        )
    }
}

val verifyBinaryCompatibilityWithJapicmp = tasks.register<JavaExec>("verifyBinaryCompatibilityWithJapicmp") {
    group = "verification"
    description = "Internal japicmp CLI invocation for the configured ActionGraph baseline version."
    classpath(japicmpCli)
    mainClass.set("japicmp.JApiCmp")
    onlyIf { binaryCompatibilityBaselineVersion.isPresent }
    dependsOn(libraryModuleDescriptions.keys.map { ":$it:jar" })
    inputs.property("baselineVersion", binaryCompatibilityBaselineVersion.orNull ?: "")
    inputs.property("excludedApis", binaryCompatibilityExcludes.joinToString(";"))
    outputs.dir(binaryCompatibilityReportDir)

    doFirst {
        val baselineVersion = binaryCompatibilityBaselineVersion.get()

        val baselineArtifacts = binaryCompatibilityBaselineArtifacts.resolvedConfiguration.resolvedArtifacts
                .filter { it.moduleVersion.id.version == baselineVersion }
                .sortedBy { it.name }
                .map { it.file }
        val expectedModules = libraryModuleDescriptions.keys.sorted()
        val missingModules = expectedModules.filter { moduleName ->
            baselineArtifacts.none { it.name == "$moduleName-$baselineVersion.jar" }
        }
        if (missingModules.isNotEmpty()) {
            throw GradleException(
                    "Missing baseline artifacts for $baselineVersion: ${missingModules.joinToString(", ")}"
            )
        }

        val currentArtifacts = expectedModules.map { moduleName ->
            project(":$moduleName").tasks.named("jar").get().outputs.files.singleFile
        }
        val reportFile = binaryCompatibilityReportDir.get().file(
                "japicmp-${baselineVersion}-to-${project.version}.md"
        ).asFile
        reportFile.parentFile.mkdirs()
        args(
                "--old", baselineArtifacts.joinToString(";") { it.absolutePath },
                "--new", currentArtifacts.joinToString(";") { it.absolutePath },
                "--only-modified",
                "--only-incompatible",
                "--error-on-binary-incompatibility",
                "--ignore-missing-classes",
                "--exclude", binaryCompatibilityExcludes.joinToString(";"),
                "--markdown",
                "--html-file", binaryCompatibilityReportDir.get().file(
                        "japicmp-${baselineVersion}-to-${project.version}.html"
                ).asFile.absolutePath,
                "--xml-file", binaryCompatibilityReportDir.get().file(
                        "japicmp-${baselineVersion}-to-${project.version}.xml"
                ).asFile.absolutePath
        )
        standardOutput = reportFile.outputStream()
        errorOutput = System.err
        logger.lifecycle("Running japicmp against ActionGraph baseline $baselineVersion")
    }
}

val verifyBinaryCompatibility = tasks.register("verifyBinaryCompatibility") {
    group = "verification"
    description = "Verifies ActionGraph binary compatibility against the configured japicmp baseline."
    if (binaryCompatibilityBaselineVersion.isPresent) {
        dependsOn(verifyBinaryCompatibilityWithJapicmp)
    }
    doLast {
        if (!binaryCompatibilityBaselineVersion.isPresent) {
            if (isAtLeastOneDotZero(project.version.toString())) {
                throw GradleException(
                        "ActionGraph ${project.version} requires actionGraphBaselineVersion or " +
                                "ACTIONGRAPH_BASELINE_VERSION for japicmp binary compatibility checks."
                )
            }
            logger.lifecycle(
                    "No ActionGraph binary compatibility baseline configured; " +
                            "japicmp will run when ACTIONGRAPH_BASELINE_VERSION or -PactionGraphBaselineVersion is set."
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyBinaryCompatibility)
}
