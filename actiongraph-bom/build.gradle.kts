javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":actiongraph-core"))
        api(project(":actiongraph-annotations"))
        api(project(":actiongraph-memory"))
        api(project(":actiongraph-interpretation"))
        api(project(":actiongraph-human-review"))
        api(project(":actiongraph-llm"))
        api(project(":actiongraph-llm-deepseek"))
        api(project(":actiongraph-persistence-jdbc"))
        api(project(":actiongraph-jdbc-spring-boot-starter"))
        api(project(":actiongraph-spring-boot-starter"))
        api(project(":actiongraph-governance"))
        api(project(":actiongraph-governance-spring-boot-starter"))
        api(project(":actiongraph-console-core"))
        api(project(":actiongraph-console-jdbc"))
        api(project(":actiongraph-human-review-spring-boot-starter"))
        api(project(":actiongraph-console-spring-boot-starter"))
    }
}
