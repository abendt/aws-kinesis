plugins {
    id("module-conventions")
    `java-library`
}

dependencies {
    api("software.amazon.kinesis:amazon-kinesis-client:3.1.0")
    implementation("org.awaitility:awaitility-kotlin:4.3.0")
}
