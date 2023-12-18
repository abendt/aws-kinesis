plugins {
    id("module-conventions")
    id("org.springframework.boot") version("3.2.0")
    id("org.jetbrains.kotlin.plugin.spring") version "1.9.21"
}

apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.0"))
    implementation(platform("org.apache.camel.springboot:camel-spring-boot-bom:4.2.0"))

    implementation("org.apache.camel.springboot:camel-aws2-kinesis-starter")
    implementation("org.apache.camel.springboot:camel-bean-starter")

    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(mapOf("group" to "org.junit.vintage", "module" to "junit-vintage-engine"))
    }

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))
    testImplementation("org.testcontainers:localstack")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
    testImplementation(project(":testcontainers-junit4-shim"))

    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}