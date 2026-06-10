javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":actiongraph-core"))
        api(project(":actiongraph-llm-deepseek"))
        api(project(":actiongraph-persistence-jdbc"))
        api(project(":actiongraph-jdbc-spring-boot-starter"))
        api(project(":actiongraph-spring-boot-starter"))
        api(project(":actiongraph-human-review-spring-boot-starter"))
        api(project(":actiongraph-console-spring-boot-starter"))
    }
}
