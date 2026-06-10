plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-console"))
    api(project(":actiongraph-control-plane-api"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    compileOnly("org.springframework:spring-web:6.1.14")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    testImplementation("com.h2database:h2:2.2.224")
}
