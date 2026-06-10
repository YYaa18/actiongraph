plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-persistence-jdbc"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.h2database:h2:2.2.224")
}
