plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-control-plane-auth"))
    api(project(":actiongraph-human-review-api"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    compileOnly("org.springframework:spring-web:6.1.14")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation(project(":actiongraph-human-review-spring-boot-starter"))
    testImplementation(project(":actiongraph-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
}
