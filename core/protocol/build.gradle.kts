plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.protobuf")
}

dependencies {
    api("com.google.protobuf:protobuf-java:4.36.0")
    testImplementation("junit:junit:4.13.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.36.0"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}

tasks.test {
    useJUnit()
    systemProperty("writeGoldenVectors", System.getProperty("writeGoldenVectors") ?: "false")
}
