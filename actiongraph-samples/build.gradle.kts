plugins {
    application
}

dependencies {
    implementation(project(":actiongraph-core"))
    implementation(project(":actiongraph-llm-deepseek"))
    implementation("org.apache.commons:commons-csv:1.14.1")
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
