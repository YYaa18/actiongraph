plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-core"))
    api(project(":actiongraph-human-review"))
    api(project(":actiongraph-human-review-jdbc"))
    api(project(":actiongraph-jdbc-spring-boot-starter"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation(project(":actiongraph-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("com.h2database:h2:2.2.224")
}
