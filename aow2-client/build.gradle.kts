plugins {
    id("java")
    id("application")
    id("jacoco")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(project(":aow2-common"))
    implementation(project(":aow2-core"))
    implementation("com.github.AlmasB.FXGL:fxgl:21")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.media", "javafx.swing")
}

application {
    mainClass = "com.aow2.client.AOW2App"
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("--enable-preview")
}
