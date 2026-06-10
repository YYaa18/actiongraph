plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-core"))
    api(project(":actiongraph-annotations"))
    api(project(":actiongraph-memory"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}
