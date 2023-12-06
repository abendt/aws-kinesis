Testcontainers currently depends on JUnit4. This pollutes the classpath and makes it easy to accidentally
import unwanted JUnit4 classes.
This module provides replacements for the JUnit4 classes Testcontainers depends on.
To allows us to exclude JUnit4 from the classpath.

Usage:
```kotlin
// exclude junit4 globally
configurations {
    all {
        exclude(module = "junit")
    }
}

// use shim instead
dependencies {
    testImplementation( ... testcontainers )
    testImplementation(project(":testcontainers-junit4-shim"))
}
```

* https://github.com/testcontainers/testcontainers-java/issues/970
* https://github.com/testcontainers/testcontainers-java/issues/970#issuecomment-625044008
