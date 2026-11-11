import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("module-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.apache.camel.springboot:camel-spring-boot-bom:4.23.0"))

    implementation("org.apache.camel.springboot:camel-aws2-kinesis-starter")
    implementation("org.apache.camel.springboot:camel-file-starter")
    implementation("org.apache.camel.springboot:camel-master-starter")
    implementation("org.apache.camel.springboot:camel-bean-starter")

    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-localstack")
    testImplementation(libs.kotest.testcontainers)
    testImplementation(project(":testcontainers-junit4-shim"))
    testImplementation(project(":test-utils"))

    testImplementation(libs.kotest.spring)
    testImplementation("com.ninja-squad:springmockk:5.0.1")
}

configurations {
    all {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
}

tasks.named("spotlessJavaCheck") {
    enabled = false
}

tasks.named<Test>("test") {
    maxParallelForks = 2
}
