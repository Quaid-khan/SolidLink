plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:protocol"))
    api(project(":core:crypto"))
    api(project(":transport:api"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
