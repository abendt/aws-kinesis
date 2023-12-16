rootProject.name = "aws-kinesis"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("./libs.versions.toml"))
        }
    }
}

include("test-utils")
include("kcl")
include("camel")
include("testcontainers-junit4-shim")
