plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-core"))
    api("com.fasterxml.jackson.core:jackson-databind:2.22.0")

    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
}
