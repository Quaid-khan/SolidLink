plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api("com.google.protobuf:protobuf-java:4.36.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
