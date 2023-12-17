plugins {
    id("module-conventions")

    id("org.springframework.boot") version ("3.2.0")
    id("org.jetbrains.kotlin.plugin.spring") version "1.9.21"

    id("jvm-test-suite")
}

apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-stream-dependencies:4.0.1"))
    implementation(platform("software.amazon.awssdk:bom:2.22.0"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kinesis:4.0.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.springframework.cloud:spring-cloud-stream-test-binder")

    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

configurations {
    all {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "com.vaadin.external.google", module = "android-json")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit.get())

            dependencies {
                implementation(project())

                implementation.bundle(libs.bundles.kotest)

                implementation("org.springframework.boot:spring-boot-starter-test")

                implementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))
                implementation("org.testcontainers:localstack")
                implementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
                implementation(project(":testcontainers-junit4-shim"))
                implementation(project(":test-utils"))

                implementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
                implementation("com.ninja-squad:springmockk:4.0.2")
            }
        }
    }
}

tasks.getByName("check").dependsOn("integrationTest")
