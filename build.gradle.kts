plugins {
    id("module-conventions")
    id("com.gtramontina.ghooks.gradle") version "2.0.0"
}

dependencies {
    implementation("software.amazon.kinesis:amazon-kinesis-client:2.5.3")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))
    testImplementation("org.testcontainers:localstack")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
}
