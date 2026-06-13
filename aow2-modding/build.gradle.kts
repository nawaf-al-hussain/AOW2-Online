plugins {
    id("java")
    id("jacoco")
}

dependencies {
    implementation(project(":aow2-common"))
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("org.slf4j:slf4j-api:2.0.12")

    testImplementation(project(":aow2-core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
