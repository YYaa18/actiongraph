plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-memory"))
    implementation(project(":actiongraph-persistence-jdbc"))

    testImplementation("com.h2database:h2:2.2.224")
}
