javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("org.slf4j:slf4j-api:2.0.17")
        api("org.jspecify:jspecify:1.0.0")
        api(project(":actiongraph-core"))
        api(project(":actiongraph-control-plane-api"))
        api(project(":actiongraph-human-review"))
        api(project(":actiongraph-llm-deepseek"))
        api(project(":actiongraph-persistence-jdbc"))
        api(project(":actiongraph-spring-boot-starter"))
        api(project(":actiongraph-governance"))
        api(project(":actiongraph-console"))
    }
}
