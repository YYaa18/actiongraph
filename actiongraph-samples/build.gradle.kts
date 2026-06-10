plugins {
    application
}

dependencies {
    implementation(project(":actiongraph-core"))
    implementation(project(":actiongraph-llm-deepseek"))
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
