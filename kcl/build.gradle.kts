plugins {
    id("module-conventions")
}

dependencies {
    implementation("software.amazon.kinesis:amazon-kinesis-client:2.6.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.8"))
    testImplementation("org.testcontainers:localstack")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
    testImplementation(project(":testcontainers-junit4-shim"))
    testImplementation(project(":test-utils"))
}

tasks.named<Test>("test") {
    maxParallelForks = 2
}
