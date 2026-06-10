plugins {
    `java-library`
}

val springBootVersion = "3.3.5"

dependencies {
    api(project(":actiongraph-core"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

    compileOnly(project(":actiongraph-persistence-jdbc"))
    compileOnly("org.springframework:spring-web:6.1.14")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    testImplementation(project(":actiongraph-persistence-jdbc"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    testImplementation("com.h2database:h2:2.2.224")
}
