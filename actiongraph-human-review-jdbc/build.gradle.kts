plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-human-review"))
    implementation(project(":actiongraph-persistence-jdbc"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.0")

    testImplementation(project(":actiongraph-governance"))
    testImplementation("com.h2database:h2:2.2.224")
}
