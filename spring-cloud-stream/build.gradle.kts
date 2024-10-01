plugins {
    id("module-conventions")

    id("org.springframework.boot") version ("3.3.4")
    id("org.jetbrains.kotlin.plugin.spring") version "2.0.20"

    id("jvm-test-suite")
}

apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.4"))
    implementation(platform("org.springframework.cloud:spring-cloud-stream-dependencies:4.1.3"))
    implementation(platform("software.amazon.awssdk:bom:2.28.12"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kinesis:4.0.4")
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

        // test suite uses Test binder
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation("org.springframework.cloud:spring-cloud-stream-test-binder")
            }
        }

        // integration suite uses Kinesis/Localstack and will use the real binder
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())

                implementation.bundle(libs.bundles.kotest)

                implementation(platform("org.testcontainers:testcontainers-bom:1.20.2"))
                implementation("org.testcontainers:localstack")
                implementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
                implementation(project(":testcontainers-junit4-shim"))
                implementation(project(":test-utils"))
            }
        }

        // common suite configuration
        withType(JvmTestSuite::class).configureEach {
            useJUnitJupiter(libs.versions.junit.get())

            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
                implementation("com.ninja-squad:springmockk:4.0.2")
            }
        }
    }
}

tasks.getByName("check").dependsOn("integrationTest")

tasks.named<Test>("integrationTest") {
    maxParallelForks = 2
}
