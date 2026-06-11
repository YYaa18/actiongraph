javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
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
