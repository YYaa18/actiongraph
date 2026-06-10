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
        api(project(":actiongraph-runtime-api"))
        api(project(":actiongraph-component-catalog"))
        api(project(":actiongraph-control-plane-api"))
        api(project(":actiongraph-control-plane-auth"))
        api(project(":actiongraph-human-review"))
        api(project(":actiongraph-llm"))
        api(project(":actiongraph-llm-deepseek"))
        api(project(":actiongraph-persistence-jdbc"))
        api(project(":actiongraph-memory-jdbc"))
        api(project(":actiongraph-human-review-jdbc"))
        api(project(":actiongraph-jdbc-spring-boot-starter"))
        api(project(":actiongraph-runtime-api-spring-boot-starter"))
        api(project(":actiongraph-component-catalog-spring-boot-starter"))
        api(project(":actiongraph-human-review-api-spring-boot-starter"))
        api(project(":actiongraph-spring-boot-starter"))
        api(project(":actiongraph-governance"))
        api(project(":actiongraph-governance-human-review"))
        api(project(":actiongraph-governance-spring-boot-starter"))
        api(project(":actiongraph-governance-human-review-spring-boot-starter"))
        api(project(":actiongraph-console"))
        api(project(":actiongraph-human-review-spring-boot-starter"))
        api(project(":actiongraph-console-spring-boot-starter"))
        api(project(":actiongraph-control-plane-spring-boot-starter"))
    }
}
