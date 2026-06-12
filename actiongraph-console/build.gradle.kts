plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-core"))
    api(project(":actiongraph-persistence-jdbc"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")

    testImplementation("com.h2database:h2:2.2.224")
}
