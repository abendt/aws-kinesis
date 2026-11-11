import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("module-conventions")

    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)

    id("jvm-test-suite")
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.springframework.cloud:spring-cloud-stream-dependencies:5.0.4"))
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:4.2.1"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.awspring.cloud:spring-cloud-aws-kinesis-stream-binder")
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
        named<JvmTestSuite>("test") {
            dependencies {
                implementation("org.springframework.cloud:spring-cloud-stream-test-binder")
            }
        }

        // integration suite uses Kinesis/Localstack and will use the real binder
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())

                implementation.bundle(libs.bundles.kotest)

                implementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
                implementation("org.testcontainers:testcontainers-localstack")
                implementation(libs.kotest.testcontainers)
                implementation(project(":testcontainers-junit4-shim"))
                implementation(project(":test-utils"))
            }
        }

        // common suite configuration
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter(libs.versions.junit.get())

            dependencies {
                implementation(platform(SpringBootPlugin.BOM_COORDINATES))
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation(libs.kotest.spring)
                implementation("com.ninja-squad:springmockk:5.0.1")
            }
        }
    }
}

tasks.named("check") {
    dependsOn("integrationTest")
}

tasks.named<Test>("integrationTest") {
    maxParallelForks = 2
}
