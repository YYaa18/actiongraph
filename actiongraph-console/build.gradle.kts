plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-core"))
    api(project(":actiongraph-persistence-jdbc"))

    testImplementation("com.h2database:h2:2.2.224")
}
