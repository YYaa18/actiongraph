javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":actiongraph-core"))
        api(project(":actiongraph-annotations"))
        api(project(":actiongraph-component-catalog"))
        api(project(":actiongraph-control-plane-api"))
        api(project(":actiongraph-human-review"))
        api(project(":actiongraph-llm"))
        api(project(":actiongraph-llm-deepseek"))
        api(project(":actiongraph-persistence-jdbc"))
        api(project(":actiongraph-spring-boot-starter"))
        api(project(":actiongraph-governance"))
        api(project(":actiongraph-console"))
        api(project(":actiongraph-console-spring-boot-starter"))
    }
}
