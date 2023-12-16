plugins {
    id("module-conventions")
    `java-library`
}

dependencies {
    api("software.amazon.kinesis:amazon-kinesis-client:2.5.3")
    implementation("org.awaitility:awaitility-kotlin:4.2.0")
}
