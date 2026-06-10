plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-console-api-spring-boot-starter"))
    api(project(":actiongraph-console-ui-spring-boot-starter"))

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
}
