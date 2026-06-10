plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-runtime-api-spring-boot-starter"))
    api(project(":actiongraph-component-catalog-spring-boot-starter"))
    api(project(":actiongraph-human-review-api-spring-boot-starter"))
    api(project(":actiongraph-human-review-callback-spring-boot-starter"))
    api(project(":actiongraph-console-api-spring-boot-starter"))
    api(project(":actiongraph-console-ui-spring-boot-starter"))
    api(project(":actiongraph-console-export-spring-boot-starter"))

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation(project(":actiongraph-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
}
