import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("module-conventions")
    id("org.springframework.boot") version("3.2.1")
    id("org.jetbrains.kotlin.plugin.spring") version "1.9.22"
}

apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    implementation(platform("org.apache.camel.springboot:camel-spring-boot-bom:4.4.0-SNAPSHOT"))

    implementation("org.apache.camel.springboot:camel-aws2-kinesis-starter")
    implementation("org.apache.camel.springboot:camel-file-starter")
    implementation("org.apache.camel.springboot:camel-master-starter")
    implementation("org.apache.camel.springboot:camel-bean-starter")

    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.4"))
    testImplementation("org.testcontainers:localstack")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
    testImplementation(project(":testcontainers-junit4-shim"))
    testImplementation(project(":test-utils"))

    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

configurations {
    all {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
}

repositories {
    maven {
        url = uri("https://repository.apache.org/content/repositories/snapshots")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

tasks.getByName("spotlessJavaCheck") {
    enabled = false
}

tasks.named<Test>("test") {
    maxParallelForks = 2
}
