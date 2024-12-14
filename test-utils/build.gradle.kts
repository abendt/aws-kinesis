plugins {
    id("module-conventions")
    `java-library`
}

dependencies {
    api("software.amazon.kinesis:amazon-kinesis-client:2.6.1")
    implementation("org.awaitility:awaitility-kotlin:4.2.2")
}
