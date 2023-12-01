import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("kotlin-conventions")
    id("com.adarshr.test-logger")
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.kotlinLogging)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk.core)

    runtimeOnly(libs.bundles.logback)
    runtimeOnly(libs.bundles.slf4j)
}

val isIdea = providers.systemProperty("idea.version")
testlogger {
    // idea can't handle ANSI output
    setTheme(if (isIdea.isPresent) "plain" else "mocha")
    showFullStackTraces = false

    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true

    showPassed = true
    showSkipped = false
    showFailed = true
}

configurations {
    all {
        exclude(module = "log4j")
        exclude(module = "mockito-core")
        exclude(module = "junit")
        exclude(module = "commons-logging")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    ratchetFrom("origin/main")

    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
    java {
        googleJavaFormat().aosp()
    }
}
