plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":core:common"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
