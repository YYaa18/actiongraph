javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":actiongraph-core"))
        api(project(":actiongraph-annotations"))
        api(project(":actiongraph-memory"))
        api(project(":actiongraph-memory-spring-boot-starter"))
        api(project(":actiongraph-interpretation"))
        api(project(":actiongraph-human-review"))
        api(project(":actiongraph-llm"))
        api(project(":actiongraph-llm-deepseek"))
        api(project(":actiongraph-persistence-jdbc"))
        api(project(":actiongraph-memory-jdbc"))
        api(project(":actiongraph-human-review-jdbc"))
        api(project(":actiongraph-jdbc-spring-boot-starter"))
        api(project(":actiongraph-memory-jdbc-spring-boot-starter"))
        api(project(":actiongraph-human-review-jdbc-spring-boot-starter"))
        api(project(":actiongraph-human-review-callback-spring-boot-starter"))
        api(project(":actiongraph-spring-boot-starter"))
        api(project(":actiongraph-governance"))
        api(project(":actiongraph-governance-human-review"))
        api(project(":actiongraph-governance-spring-boot-starter"))
        api(project(":actiongraph-governance-human-review-spring-boot-starter"))
        api(project(":actiongraph-console-core"))
        api(project(":actiongraph-console-jdbc"))
        api(project(":actiongraph-console-export"))
        api(project(":actiongraph-console-spring-boot-autoconfigure"))
        api(project(":actiongraph-console-api-spring-boot-starter"))
        api(project(":actiongraph-console-ui-spring-boot-starter"))
        api(project(":actiongraph-console-export-spring-boot-starter"))
        api(project(":actiongraph-console-jdbc-spring-boot-starter"))
        api(project(":actiongraph-human-review-spring-boot-starter"))
        api(project(":actiongraph-console-spring-boot-starter"))
    }
}
