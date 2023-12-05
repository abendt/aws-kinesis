import gradle.kotlin.dsl.accessors._749471404c71924f08255562a758d0eb.java
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()
val javaVersion = libs.versions.java.get()

java {
    targetCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
}

kotlin {
    jvmToolchain(javaVersion.toInt())
}

dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

val isIdea = providers.systemProperty("idea.version").isPresent

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        // don't fail the build when running tests in idea!
        allWarningsAsErrors = !isIdea

        incremental = true
        freeCompilerArgs = listOf("-Xjsr305=strict")

        if (isIdea) {
            freeCompilerArgs += "-Xdebug"
        }
    }
}
