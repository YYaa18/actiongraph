plugins {
    application
}

dependencies {
    implementation(project(":actiongraph-core"))
    implementation(project(":actiongraph-memory"))
    implementation(project(":actiongraph-interpretation"))
    implementation(project(":actiongraph-governance"))
    implementation(project(":actiongraph-llm-deepseek"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
    implementation("org.apache.commons:commons-csv:1.14.1")

    testImplementation("com.h2database:h2:2.2.224")
}

application {
    mainClass.set("com.actiongraph.samples.renewal.RenewalQuoteSampleApp")
}

tasks.register<JavaExec>("runOrderCancellationSample") {
    group = "application"
    description = "Runs the order cancellation sample app."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.actiongraph.samples.ordercancellation.OrderCancellationSampleApp")
}

tasks.register<JavaExec>("runClaimsPrecheckSample") {
    group = "application"
    description = "Runs the claims precheck sample app."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.actiongraph.samples.claimsprecheck.ClaimsPrecheckSampleApp")
}

tasks.register<JavaExec>("runClaimsPrecheckBatchMetrics") {
    group = "application"
    description = "Runs the claims precheck batch metrics sample."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.actiongraph.samples.claimsprecheck.batch.ClaimsPrecheckBatchMetricsApp")
    workingDir = rootProject.projectDir
}
