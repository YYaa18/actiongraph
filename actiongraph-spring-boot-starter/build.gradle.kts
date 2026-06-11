plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-core"))
    api(project(":actiongraph-annotations"))
    api(project(":actiongraph-control-plane-api"))
    api(project(":actiongraph-governance"))
    api(project(":actiongraph-human-review"))
    api(project(":actiongraph-persistence-jdbc"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    compileOnly("org.springframework:spring-web:6.1.14")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    testImplementation("com.h2database:h2:2.2.224")
}
