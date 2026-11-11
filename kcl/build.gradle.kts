plugins {
    id("module-conventions")
}

dependencies {
    implementation("software.amazon.kinesis:amazon-kinesis-client:3.5.4")
    implementation("io.github.resilience4j:resilience4j-retry:2.4.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.23.0")

    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-localstack")
    testImplementation(libs.kotest.testcontainers)
    testImplementation(project(":testcontainers-junit4-shim"))
    testImplementation(project(":test-utils"))
}

tasks.named<Test>("test") {
    maxParallelForks = 2
}
