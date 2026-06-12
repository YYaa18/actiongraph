plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
