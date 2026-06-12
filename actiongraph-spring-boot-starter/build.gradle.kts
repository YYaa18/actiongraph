plugins {
    `java-library`
}

val springBootVersion = "3.3.5"
val openTelemetryVersion = "1.63.0"

dependencies {
    api(project(":actiongraph-core"))
    api(project(":actiongraph-control-plane-api"))
    api(project(":actiongraph-console"))
    api(project(":actiongraph-governance"))
    api(project(":actiongraph-human-review"))
    api(project(":actiongraph-llm-deepseek"))
    api(project(":actiongraph-persistence-jdbc"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    implementation("org.slf4j:slf4j-api:2.0.17")

    compileOnly("org.springframework:spring-web:6.1.14")
    compileOnly("io.micrometer:micrometer-core:1.13.6")
    compileOnly("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    testImplementation("org.springframework.security:spring-security-oauth2-resource-server:6.3.4")
    testImplementation("org.springframework.security:spring-security-oauth2-jose:6.3.4")
    testImplementation("org.springframework.security:spring-security-test:6.3.4")
    testImplementation("io.micrometer:micrometer-core:1.13.6")
    testImplementation("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")
    testImplementation("io.opentelemetry:opentelemetry-sdk:$openTelemetryVersion")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:$openTelemetryVersion")
    testImplementation("io.opentelemetry:opentelemetry-sdk-trace:$openTelemetryVersion")
    testImplementation("com.h2database:h2:2.2.224")
}
