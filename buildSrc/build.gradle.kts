plugins {
    `kotlin-dsl`
    id("com.diffplug.spotless") version libs.versions.gradleSpotlessPlugin.get()
}

repositories {
    gradlePluginPortal()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

dependencies {
    val kotlinVersion = libs.versions.kotlin.get()
    val testLoggerPluginVersion = libs.versions.gradleTestLoggerPlugin.get()
    val spotlessVersion = libs.versions.gradleSpotlessPlugin.get()

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("com.adarshr:gradle-test-logger-plugin:$testLoggerPluginVersion")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:$spotlessVersion")

    // workaround to be able to use the version catalog inside of gradle convention plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    ratchetFrom("origin/main")

    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
