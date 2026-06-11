plugins {
    `java-library`
}

dependencies {
    api(project(":actiongraph-core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    api("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
